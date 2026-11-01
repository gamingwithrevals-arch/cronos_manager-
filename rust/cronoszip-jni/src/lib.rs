//! The Android bridge. A thin JSON-in, JSON-out layer over `hatchzip-core`,
//! plus one callback for live progress. All decisions (space, safety,
//! resuming) are the engine's; this file only reports them.

mod compress;

use std::io::ErrorKind;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{bail, Context, Result};
use hatchzip_core::archive::storage::Reads;
use hatchzip_core::archive::{self, Item, Kind};
use hatchzip_core::engine::{self, Outcome, Progress, Run};
use hatchzip_core::replace;
use hatchzip_core::restore;
use hatchzip_core::session::{self, State};
use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::sys::jstring;
use jni::{JNIEnv, JavaVM};
use serde::Deserialize;
use serde_json::{Value, json};

static STOP: AtomicBool = AtomicBool::new(false);

const MAX_LIST_ENTRIES: usize = 5000;
const DEFAULT_KEEP_FREE: u64 = 2 * 1024 * 1024 * 1024;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CheckConfig {
    archive: String,
    #[serde(default)]
    output: Option<String>,
    #[serde(default)]
    keep_archive: bool,
    #[serde(default = "default_keep_free")]
    keep_free: u64,
    #[serde(default)]
    password: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ExtractConfig {
    archive: String,
    #[serde(default)]
    output: Option<String>,
    #[serde(default)]
    keep_archive: bool,
    #[serde(default = "default_replace")]
    replace: String,
    #[serde(default)]
    verify_first: bool,
    #[serde(default = "default_keep_free")]
    keep_free: u64,
    #[serde(default)]
    password: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ArchiveConfig {
    archive: String,
    #[serde(default)]
    password: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RestoreConfig {
    archive: String,
    #[serde(default)]
    output: Option<String>,
}

fn default_keep_free() -> u64 {
    DEFAULT_KEEP_FREE
}

fn default_replace() -> String {
    "none".into()
}

/// A failure a front end can act on, not just print.
struct EngineError {
    code: &'static str,
    message: String,
    data: Value,
}

impl EngineError {
    fn new(code: &'static str, message: impl Into<String>, data: Value) -> Self {
        Self {
            code,
            message: message.into(),
            data,
        }
    }

    fn from_anyhow(error: anyhow::Error, saved: bool) -> Self {
        let (code, message) = classify(&error);
        Self {
            code,
            message,
            data: json!({ "saved": saved }),
        }
    }
}

impl From<anyhow::Error> for EngineError {
    fn from(error: anyhow::Error) -> Self {
        Self::from_anyhow(error, false)
    }
}

fn classify(error: &anyhow::Error) -> (&'static str, String) {
    let (mut wrong, mut needed, mut damaged) = (false, false, false);
    let (mut full, mut busy) = (false, false);
    for cause in error.chain() {
        if cause.downcast_ref::<archive::WrongPassword>().is_some() {
            wrong = true;
        } else if cause.downcast_ref::<archive::PasswordNeeded>().is_some() {
            needed = true;
        } else if cause.downcast_ref::<archive::Damaged>().is_some() {
            damaged = true;
        } else if let Some(io) = cause.downcast_ref::<std::io::Error>() {
            if io.raw_os_error() == Some(28) || io.kind() == ErrorKind::StorageFull {
                full = true;
            }
            if io.kind() == ErrorKind::WouldBlock {
                busy = true;
            }
        }
    }
    let message = format!("{error:#}");
    if wrong {
        (
            "WRONG_PASSWORD",
            "That password does not open this archive. Nothing was changed.".into(),
        )
    } else if needed {
        (
            "PASSWORD_NEEDED",
            "This archive is password protected. Enter its password.".into(),
        )
    } else if damaged {
        ("DAMAGED", message)
    } else if full || message.to_ascii_lowercase().contains("not enough disk space") {
        ("NO_SPACE", message)
    } else if busy {
        (
            "BUSY",
            "Another extraction is already using this archive. Close it and try again.".into(),
        )
    } else {
        ("FAILED", message)
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

struct Emitter {
    vm: JavaVM,
    callback: GlobalRef,
    last: AtomicU64,
}

impl Emitter {
    fn new(vm: JavaVM, callback: GlobalRef) -> Self {
        Self {
            vm,
            callback,
            last: AtomicU64::new(0),
        }
    }

    fn emit(&self, event: Value) {
        let Ok(mut env) = self.vm.attach_current_thread_permanently() else {
            return;
        };
        let Ok(text) = env.new_string(event.to_string()) else {
            return;
        };
        let payload: JObject = text.into();
        let _ = env.call_method(
            self.callback.as_obj(),
            "onEvent",
            "(Ljava/lang/String;)V",
            &[JValue::Object(&payload)],
        );
        let _ = env.delete_local_ref(payload);
    }

    fn emit_throttled(&self, event: Value) {
        let now = now_ms();
        let previous = self.last.load(Ordering::Relaxed);
        if now.saturating_sub(previous) < 100 {
            return;
        }
        if self
            .last
            .compare_exchange(previous, now, Ordering::Relaxed, Ordering::Relaxed)
            .is_err()
        {
            return;
        }
        self.emit(event);
    }

    fn phase(&self, phase: &str, message: &str) {
        self.emit(json!({ "type": "phase", "phase": phase, "message": message }));
    }

    fn log(&self, message: &str) {
        self.emit(json!({ "type": "log", "message": message }));
    }

    fn file(&self, path: &str) {
        self.emit_throttled(json!({ "type": "file", "current": path }));
    }

    fn progress(&self, done: u64, total: u64) {
        self.emit_throttled(json!({ "type": "progress", "done": done, "total": total }));
    }

    fn verify_progress(&self, done: u64, total: u64) {
        self.emit_throttled(json!({
            "type": "progress", "phase": "verifying", "done": done, "total": total
        }));
    }

    fn commit(&self, committed: u64, archive_size: u64) {
        self.emit(json!({
            "type": "commit", "committed": committed, "archiveSize": archive_size
        }));
    }
}

fn ok_value(data: Value) -> Value {
    json!({ "ok": true, "code": "OK", "message": "", "data": data })
}

fn error_value(error: &EngineError) -> Value {
    json!({
        "ok": false,
        "code": error.code,
        "message": error.message,
        "data": error.data,
    })
}

fn reply(env: &mut JNIEnv<'_>, value: Value) -> jstring {
    match env.new_string(value.to_string()) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String> {
    Ok(env
        .get_string(value)
        .context("reading the request from Android")?
        .into())
}

fn parse_config<T: serde::de::DeserializeOwned>(
    env: &mut JNIEnv<'_>,
    value: &JString<'_>,
) -> Result<T> {
    let raw = read_string(env, value)?;
    serde_json::from_str(&raw).context("reading the request from Android")
}

fn path_string(path: &std::path::Path) -> String {
    path.to_string_lossy().into_owned()
}

fn plan_json(plan: &session::SpacePlan) -> Value {
    json!({
        "free": plan.free,
        "needed": plan.needed,
        "keepFree": plan.keep_free,
        "fits": plan.fits,
        "freeAfter": plan.free_after,
        "keepArchive": plan.keep_archive,
        "normalNeeds": plan.normal_needs,
        "inPlaceNeeds": plan.in_place_needs,
        "either": plan.either,
        "reclaims": plan.reclaims,
        "solidBlockBound": plan.solid_block_bound,
        "archiveKeptWhole": plan.archive_kept_whole,
    })
}

fn empty_plan(keep_free: u64) -> Value {
    json!({
        "free": 0, "needed": 0, "keepFree": keep_free, "fits": true,
        "freeAfter": 0, "keepArchive": false, "normalNeeds": 0,
        "inPlaceNeeds": 0, "either": true, "reclaims": false,
        "solidBlockBound": false, "archiveKeptWhole": false,
    })
}

fn no_space(plan: &session::SpacePlan, saved: bool) -> EngineError {
    let required = plan.needed.saturating_add(plan.keep_free);
    let message = format!(
        "Not enough free space: {} is required and {} is available. Nothing was changed. \
         Free up space or choose another destination.",
        engine::human(required),
        engine::human(plan.free)
    );
    EngineError::new(
        "NO_SPACE",
        message,
        json!({ "saved": saved, "plan": plan_json(plan) }),
    )
}

fn millis(time: Option<SystemTime>) -> Value {
    match time
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_millis() as u64)
    {
        Some(ms) => json!(ms),
        None => Value::Null,
    }
}

fn entry_json(item: &Item) -> Value {
    let (kind, target) = match &item.kind {
        Kind::File => ("file", Value::Null),
        Kind::Dir => ("dir", Value::Null),
        Kind::Symlink => ("symlink", Value::Null),
        Kind::HardLink(target) => ("hardlink", json!(path_string(target))),
    };
    json!({
        "path": path_string(&item.path),
        "size": item.size,
        "kind": kind,
        "target": target,
        "mode": item.mode,
        "mtime": millis(item.mtime),
    })
}

fn check_inner(cfg: CheckConfig) -> Result<Value> {
    let archive = PathBuf::from(&cfg.archive);
    let (primary, paths) = session::locate(&archive)?;
    let reads = Arc::new(Reads::default());
    let output = cfg
        .output
        .as_deref()
        .filter(|value| !value.is_empty())
        .map(PathBuf::from);
    let password = cfg.password.as_deref().filter(|value| !value.is_empty());
    let state = session::open(&primary, &paths, output, false, password, &reads)?;

    if let State::Orphan(journal) = &state {
        return Ok(json!({
            "archive": path_string(&primary),
            "displayName": path_string(&primary),
            "format": "unknown",
            "archiveSize": 0,
            "entryCount": 0,
            "uncompressedSize": 0,
            "totalSize": 0,
            "encrypted": false,
            "resuming": false,
            "resumeKeepsArchive": false,
            "orphan": true,
            "output": path_string(&journal.output),
            "existingFiles": [],
            "singleRoot": false,
            "inPlaceRefusal": Value::Null,
            "plan": empty_plan(cfg.keep_free),
        }));
    }

    let (job, out, journal) = match state {
        State::Fresh(job, out) => (job, out, None),
        State::Resume(job, journal) => {
            let keep = session::keeps_archive(&journal, cfg.keep_archive)?;
            let out = journal.output.clone();
            (job, out, Some((journal, keep)))
        }
        State::Orphan(_) => unreachable!(),
    };
    let resuming = journal.is_some();
    let keep = journal.as_ref().map_or(cfg.keep_archive, |(_, keep)| *keep);
    let plan = session::plan(&job, &out, resuming, cfg.keep_free, keep)?;
    let existing = if resuming {
        Vec::new()
    } else {
        session::existing(&job, &out).unwrap_or_default()
    };
    let existing_files: Vec<String> = existing
        .iter()
        .take(100)
        .map(|path| path_string(path))
        .collect();
    let total_size = journal
        .as_ref()
        .map_or_else(|| job.remaining_size(), |(journal, _)| journal.total_size);
    let committed_len = journal
        .as_ref()
        .map_or_else(|| job.storage.original_len(), |(journal, _)| journal.committed_len);

    Ok(json!({
        "archive": path_string(&primary),
        "displayName": session::display_name(&job),
        "format": job.format_name,
        "archiveSize": job.storage.original_len(),
        "entryCount": job.item_count(),
        "uncompressedSize": job.remaining_size(),
        "totalSize": total_size,
        "committedLen": committed_len,
        "encrypted": job.encrypted(),
        "resuming": resuming,
        "resumeKeepsArchive": keep,
        "orphan": false,
        "output": path_string(&out),
        "existingFiles": existing_files,
        "existingCount": existing.len(),
        "singleRoot": session::single_root(&job),
        "inPlaceRefusal": job.in_place_refusal(),
        "plan": plan_json(&plan),
    }))
}

fn open_readonly(archive: &str, password: Option<&str>) -> Result<(hatchzip_core::engine::Job, bool)> {
    let archive = PathBuf::from(archive);
    let (primary, paths) = session::locate(&archive)?;
    let reads = Arc::new(Reads::default());
    let state = session::open(&primary, &paths, None, false, password, &reads)?;
    match state {
        State::Fresh(job, _) => Ok((job, false)),
        State::Resume(job, _) => Ok((job, true)),
        State::Orphan(_) => bail!(
            "every entry of this archive is already extracted; run extraction again to finish \
             the cleanup"
        ),
    }
}

fn list_inner(cfg: ArchiveConfig) -> Result<Value> {
    let password = cfg.password.as_deref().filter(|value| !value.is_empty());
    let (job, resuming) = open_readonly(&cfg.archive, password)?;
    let mut entries = Vec::new();
    let mut truncated = false;
    'outer: for item in job.units.iter().rev().flat_map(|unit| &unit.items) {
        if entries.len() >= MAX_LIST_ENTRIES {
            truncated = true;
            break 'outer;
        }
        entries.push(entry_json(item));
    }
    Ok(json!({
        "archive": path_string(job.storage.primary()),
        "displayName": session::display_name(&job),
        "format": job.format_name,
        "archiveSize": job.storage.original_len(),
        "totalSize": job.remaining_size(),
        "entryCount": job.item_count(),
        "encrypted": job.encrypted(),
        "resuming": resuming,
        "entries": entries,
        "truncated": truncated,
    }))
}

fn test_inner(cfg: ArchiveConfig, emitter: Arc<Emitter>) -> Result<Value> {
    STOP.store(false, Ordering::SeqCst);
    let password = cfg.password.as_deref().filter(|value| !value.is_empty());
    let (job, resuming) = open_readonly(&cfg.archive, password)?;
    if resuming {
        bail!(
            "this archive is partially extracted and cannot be tested. Resume the extraction, \
             or restore the archive first"
        );
    }
    let total = job.storage.original_len();
    let done = AtomicU64::new(0);
    emitter.phase("verifying", "Reading every entry and checking its checksum");
    job.verify(&|bytes| {
        let done = done.fetch_add(bytes, Ordering::Relaxed) + bytes;
        emitter.verify_progress(done, total);
    })?;
    emitter.phase("complete", "Every entry passed");
    Ok(json!({
        "entries": job.item_count(),
        "bytes": total,
        "archive": path_string(job.storage.primary()),
    }))
}

fn extract_inner(
    cfg: ExtractConfig,
    emitter: Arc<Emitter>,
) -> std::result::Result<Value, EngineError> {
    STOP.store(false, Ordering::SeqCst);
    let archive = PathBuf::from(&cfg.archive);
    let (primary, paths) = session::locate(&archive).map_err(EngineError::from)?;
    let reads = Arc::new(Reads::default());
    let output = cfg
        .output
        .as_deref()
        .filter(|value| !value.is_empty())
        .map(PathBuf::from);
    let password = cfg.password.as_deref().filter(|value| !value.is_empty());

    emitter.phase("preparing", "Opening the archive and reading its index");
    let state = session::open(&primary, &paths, output, true, password, &reads)
        .map_err(EngineError::from)?;

    let (job, journal) = match state {
        State::Orphan(journal) => {
            emitter.phase("finishing", "Finishing an extraction that already completed");
            engine::finish_orphaned(&journal, &paths).map_err(EngineError::from)?;
            return Ok(json!({
                "paused": false,
                "orphanCleaned": true,
                "kept": journal.keep,
                "itemCount": 0,
                "totalSize": 0,
                "output": path_string(&journal.output),
                "archive": path_string(&primary),
            }));
        }
        State::Resume(job, journal) => {
            let keep = session::keeps_archive(&journal, cfg.keep_archive)
                .map_err(EngineError::from)?;
            emitter.log(if keep {
                "Resuming. This extraction keeps the archive."
            } else {
                "Resuming where the last run stopped. The archive continues shrinking."
            });
            let plan = session::plan(&job, &journal.output, true, cfg.keep_free, keep)
                .map_err(EngineError::from)?;
            if !plan.fits {
                return Err(no_space(&plan, true));
            }
            session::clear_uncommitted(&job, &journal.output).map_err(EngineError::from)?;
            (job, journal)
        }
        State::Fresh(job, out) => {
            if !cfg.keep_archive {
                if let Some(reason) = job.in_place_refusal() {
                    return Err(EngineError::new(
                        "IN_PLACE_REFUSED",
                        format!(
                            "{reason}. Enable \"Keep archive\" to extract this archive without \
                             shrinking it."
                        ),
                        json!({ "saved": false }),
                    ));
                }
            }
            let found = session::existing(&job, &out).map_err(EngineError::from)?;
            if !found.is_empty() && cfg.replace == "none" {
                let files: Vec<String> = found
                    .iter()
                    .take(20)
                    .map(|path| path_string(path))
                    .collect();
                return Err(EngineError::new(
                    "EXISTS",
                    format!(
                        "{} file{} already exist in the destination. Choose whether to replace \
                         them or keep them.",
                        found.len(),
                        if found.len() == 1 { "" } else { "s" }
                    ),
                    json!({ "saved": false, "files": files }),
                ));
            }
            let plan = session::plan(&job, &out, false, cfg.keep_free, cfg.keep_archive)
                .map_err(EngineError::from)?;
            if !plan.fits {
                return Err(no_space(&plan, false));
            }
            emitter.log(&format!(
                "Space check passed. {} available, {} required, {} kept free.",
                engine::human(plan.free),
                engine::human(plan.needed),
                engine::human(plan.keep_free)
            ));

            if cfg.verify_first {
                emitter.phase(
                    "verifying",
                    "Checking every entry before anything is deleted",
                );
                let total = job.storage.original_len();
                let done = AtomicU64::new(0);
                job.verify(&|bytes| {
                    let done = done.fetch_add(bytes, Ordering::Relaxed) + bytes;
                    emitter.verify_progress(done, total);
                })
                .map_err(|error| EngineError::from_anyhow(error, false))?;
                emitter.log("Every entry passed verification.");
            }

            let choices = match cfg.replace.as_str() {
                "skip" => Some(replace::Choices {
                    keep: found.clone(),
                    both: Vec::new(),
                }),
                "replace" => Some(replace::Choices::default()),
                _ => None,
            };
            let journal =
                session::begin(&job, &paths, out, cfg.keep_archive, choices.as_ref())
                    .map_err(EngineError::from)?;
            (job, journal)
        }
    };

    let keep = journal.keep;
    let output = journal.output.clone();
    let count = job.item_count();
    let total = journal.total_size;
    let archive_size = job.storage.original_len();
    let initial_done = total.saturating_sub(job.remaining_size());

    emitter.phase("extracting", "Extracting entries from the end of the archive");
    emitter.log(&format!(
        "{} {}, {} uncompressed, format {}.",
        count,
        if count == 1 { "entry" } else { "entries" },
        engine::human(total),
        job.format_name
    ));
    emitter.commit(journal.committed_len, archive_size);
    emitter.progress(initial_done, total);

    let done = Arc::new(AtomicU64::new(initial_done));
    let bytes_done = done.clone();
    let bytes_emitter = emitter.clone();
    let commit_emitter = emitter.clone();
    let unit_emitter = emitter.clone();
    let progress = Progress {
        on_bytes: Box::new(move |bytes| {
            let done = bytes_done.fetch_add(bytes, Ordering::Relaxed) + bytes;
            bytes_emitter.progress(done, total);
        }),
        on_commit: Box::new(move |committed| {
            commit_emitter.commit(committed, archive_size);
        }),
        on_unit: Box::new(move |item| {
            unit_emitter.file(&path_string(&item.path));
        }),
    };
    let run = Run {
        job,
        journal,
        paths,
        stop: &STOP,
        progress,
        keep_free: cfg.keep_free,
    };
    let outcome = run
        .execute()
        .map_err(|error| EngineError::from_anyhow(error, true))?;

    match outcome {
        Outcome::Finished => {
            emitter.phase("complete", "Extraction complete");
            emitter.progress(total, total);
            Ok(json!({
                "paused": false,
                "kept": keep,
                "itemCount": count,
                "totalSize": total,
                "output": path_string(&output),
                "archive": path_string(&primary),
            }))
        }
        Outcome::Paused => {
            emitter.phase("paused", "Paused. Progress is saved.");
            Ok(json!({
                "paused": true,
                "kept": keep,
                "itemCount": count,
                "totalSize": total,
                "output": path_string(&output),
                "archive": path_string(&primary),
            }))
        }
    }
}

fn restore_inner(cfg: RestoreConfig) -> Result<Value> {
    let archive = PathBuf::from(&cfg.archive);
    let output = cfg
        .output
        .as_deref()
        .filter(|value| !value.is_empty())
        .map(PathBuf::from);
    let restored = restore::restore(&archive, output.as_deref())?;
    Ok(json!({
        "archive": path_string(&restored.archive),
        "format": restored.format,
        "entries": restored.entries,
        "output": path_string(&restored.output),
        "kept": restored.kept,
        "setAside": restored.set_aside,
    }))
}

fn compress_inner(cfg: compress::CompressConfig) -> Result<Value> {
    let (entries, bytes) = compress::compress(cfg)?;
    Ok(json!({ "entries": entries, "bytes": bytes }))
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeCheck<'local>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    config: JString<'local>,
) -> jstring {
    let result = parse_config::<CheckConfig>(&mut env, &config).and_then(check_inner);
    match result {
        Ok(data) => reply(&mut env, ok_value(data)),
        Err(error) => reply(&mut env, error_value(&EngineError::from(error))),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeList<'local>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    config: JString<'local>,
) -> jstring {
    let result = parse_config::<ArchiveConfig>(&mut env, &config).and_then(list_inner);
    match result {
        Ok(data) => reply(&mut env, ok_value(data)),
        Err(error) => reply(&mut env, error_value(&EngineError::from(error))),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeTest<'local>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    config: JString<'local>,
    callback: JObject<'local>,
) -> jstring {
    let parsed = match parse_config::<ArchiveConfig>(&mut env, &config) {
        Ok(value) => value,
        Err(error) => return reply(&mut env, error_value(&EngineError::from(error))),
    };
    let vm = match env.get_java_vm() {
        Ok(vm) => vm,
        Err(error) => {
            return reply(
                &mut env,
                error_value(&EngineError::new(
                    "FAILED",
                    format!("connecting to the Android runtime: {error}"),
                    json!({ "saved": false }),
                )),
            );
        }
    };
    let callback = match env.new_global_ref(&callback) {
        Ok(reference) => reference,
        Err(error) => {
            return reply(
                &mut env,
                error_value(&EngineError::new(
                    "FAILED",
                    format!("keeping the Android callback: {error}"),
                    json!({ "saved": false }),
                )),
            );
        }
    };
    let emitter = Arc::new(Emitter::new(vm, callback));
    match test_inner(parsed, emitter) {
        Ok(data) => reply(&mut env, ok_value(data)),
        Err(error) => reply(&mut env, error_value(&EngineError::from(error))),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeExtract<'local>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    config: JString<'local>,
    callback: JObject<'local>,
) -> jstring {
    let parsed = match parse_config::<ExtractConfig>(&mut env, &config) {
        Ok(value) => value,
        Err(error) => return reply(&mut env, error_value(&EngineError::from(error))),
    };
    let vm = match env.get_java_vm() {
        Ok(vm) => vm,
        Err(error) => {
            return reply(
                &mut env,
                error_value(&EngineError::new(
                    "FAILED",
                    format!("connecting to the Android runtime: {error}"),
                    json!({ "saved": false }),
                )),
            );
        }
    };
    let callback = match env.new_global_ref(&callback) {
        Ok(reference) => reference,
        Err(error) => {
            return reply(
                &mut env,
                error_value(&EngineError::new(
                    "FAILED",
                    format!("keeping the Android callback: {error}"),
                    json!({ "saved": false }),
                )),
            );
        }
    };
    let emitter = Arc::new(Emitter::new(vm, callback));
    match extract_inner(parsed, emitter) {
        Ok(data) => reply(&mut env, ok_value(data)),
        Err(error) => reply(&mut env, error_value(&error)),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeRestore<'local>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    config: JString<'local>,
) -> jstring {
    let result = parse_config::<RestoreConfig>(&mut env, &config).and_then(restore_inner);
    match result {
        Ok(data) => reply(&mut env, ok_value(data)),
        Err(error) => reply(&mut env, error_value(&EngineError::from(error))),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeCompress<'local>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    config: JString<'local>,
) -> jstring {
    let result = parse_config::<compress::CompressConfig>(&mut env, &config).and_then(compress_inner);
    match result {
        Ok(data) => reply(&mut env, ok_value(data)),
        Err(error) => reply(&mut env, error_value(&EngineError::from(error))),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cronoszip_app_engine_NativeBridge_nativeCancel<'local>(
    _env: JNIEnv<'local>,
    _this: JObject<'local>,
) {
    STOP.store(true, Ordering::SeqCst);
}
