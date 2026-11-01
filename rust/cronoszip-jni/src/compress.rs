//! Builds a ZIP archive from files and folders. The file manager's Compress action calls this.
//! It writes to a temporary "<name>.part" file and renames it only when everything succeeded,
//! so a failed or cancelled run never leaves a half-written archive behind.

use std::fs::{self, File};
use std::io;
use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::Deserialize;
use zip::write::{SimpleFileOptions, ZipWriter};
use zip::CompressionMethod;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompressConfig {
    pub output: String,
    pub inputs: Vec<String>,
    #[serde(default)]
    pub store: bool,
}

/// Returns (entries written, uncompressed bytes).
pub fn compress(cfg: CompressConfig) -> Result<(usize, u64)> {
    let output = PathBuf::from(&cfg.output);
    if cfg.inputs.is_empty() {
        bail!("Nothing to compress.");
    }
    if output.exists() {
        bail!("A file named {} already exists. Choose another name.", output.display());
    }
    let part = PathBuf::from(format!("{}.part", cfg.output));
    let file = File::create(&part).with_context(|| format!("creating {}", part.display()))?;
    let mut writer = ZipWriter::new(file);
    let method = if cfg.store {
        CompressionMethod::Stored
    } else {
        CompressionMethod::Deflated
    };
    let options = SimpleFileOptions::default().compression_method(method);
    let mut count = 0usize;
    let mut bytes = 0u64;

    if let Err(error) = write_inputs(&mut writer, &cfg.inputs, options, &mut count, &mut bytes) {
        let _ = fs::remove_file(&part);
        return Err(error);
    }
    writer.finish().context("finishing the archive")?;
    fs::rename(&part, &output).with_context(|| format!("saving {}", output.display()))?;
    Ok((count, bytes))
}

fn write_inputs(
    writer: &mut ZipWriter<File>,
    inputs: &[String],
    options: SimpleFileOptions,
    count: &mut usize,
    bytes: &mut u64,
) -> Result<()> {
    for input in inputs {
        let path = PathBuf::from(input);
        let name = path
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_else(|| "item".to_string());
        add_path(writer, &path, &name, options, count, bytes)?;
    }
    Ok(())
}

fn add_path(
    writer: &mut ZipWriter<File>,
    path: &Path,
    name: &str,
    options: SimpleFileOptions,
    count: &mut usize,
    bytes: &mut u64,
) -> Result<()> {
    let meta = fs::symlink_metadata(path).with_context(|| format!("reading {}", path.display()))?;
    if meta.file_type().is_symlink() {
        // Links are skipped, so a link cannot pull in files outside the selection.
        return Ok(());
    }
    if meta.is_dir() {
        writer
            .add_directory(format!("{name}/"), options)
            .with_context(|| format!("adding folder {name}"))?;
        *count += 1;
        let mut children: Vec<PathBuf> = fs::read_dir(path)?
            .filter_map(|entry| entry.ok().map(|e| e.path()))
            .collect();
        children.sort();
        for child in children {
            let child_name = child
                .file_name()
                .map(|n| n.to_string_lossy().into_owned())
                .unwrap_or_else(|| "item".to_string());
            add_path(writer, &child, &format!("{name}/{child_name}"), options, count, bytes)?;
        }
    } else if meta.is_file() {
        writer
            .start_file(name, options)
            .with_context(|| format!("adding {name}"))?;
        let mut source = File::open(path).with_context(|| format!("opening {}", path.display()))?;
        let copied = io::copy(&mut source, writer)?;
        *bytes += copied;
        *count += 1;
    }
    Ok(())
}
