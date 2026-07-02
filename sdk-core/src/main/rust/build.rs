//! Generates the C header for the FFM boundary via cbindgen.
//!
//! The header path can be overridden with the `SHARED_CORE_HEADER_OUT` env var
//! (set by the Gradle build so jextract can locate it); otherwise it is written
//! next to the build artifacts in `OUT_DIR`.

use std::env;
use std::path::PathBuf;

fn main() {
    let crate_dir = env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR not set");

    let out_path = env::var("SHARED_CORE_HEADER_OUT")
        .map(PathBuf::from)
        .unwrap_or_else(|_| {
            PathBuf::from(env::var("OUT_DIR").expect("OUT_DIR not set")).join("sharedcore.h")
        });

    if let Some(parent) = out_path.parent() {
        std::fs::create_dir_all(parent).expect("failed to create header output dir");
    }

    let config = cbindgen::Config {
        language: cbindgen::Language::C,
        pragma_once: true,
        cpp_compat: true,
        documentation: true,
        // Emit only the system headers we actually need. Without this, cbindgen's
        // default `#include <stdlib.h>`/`<stdarg.h>` pull the entire libc surface
        // into jextract, which then fails on unsupported types (long double, etc.).
        no_includes: true,
        sys_includes: vec!["stdint.h".to_string(), "stdbool.h".to_string()],
        // Tagged-union results/notification share variant names (Ok/Err); prefix the generated
        // tag constants with the enum name so they don't collide as jextract symbols.
        enumeration: cbindgen::EnumConfig {
            prefix_with_name: true,
            ..Default::default()
        },
        ..Default::default()
    };

    cbindgen::Builder::new()
        .with_crate(crate_dir)
        .with_config(config)
        .generate()
        .expect("cbindgen failed to generate the C header")
        .write_to_file(&out_path);

    println!("cargo:rerun-if-changed=src/lib.rs");
    println!("cargo:rerun-if-env-changed=SHARED_CORE_HEADER_OUT");
    // Track the generated header itself: if it's deleted (e.g. by `gradle clean` or a git
    // worktree switch) while the Rust sources are unchanged, a plain `cargo build` would
    // otherwise be a no-op and never re-run this script. A missing rerun-if-changed path
    // forces cargo to re-run the build script, regenerating the header.
    println!("cargo:rerun-if-changed={}", out_path.display());
}
