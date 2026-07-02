use bytes::Bytes;
use std::slice;

/// A borrowed view into caller (Java) memory: the Java side owns the allocation (its confined
/// `Arena`) and frees it; Rust only reads it for the duration of the call and must **never** free
/// it. Used for transient inputs — keys, names, ids, and the encoded blobs (headers, the await
/// future tree, failures, retry policy). Contrast `Slice`, which is Rust-allocator memory Rust owns.
#[derive(Clone, Copy)]
#[repr(C)]
pub struct ForeignSlice {
    pub ptr: *const u8,
    pub len: usize,
}

impl ForeignSlice {
    /// Borrow the foreign bytes for the duration of the call (Java keeps ownership and frees them).
    #[inline]
    pub(crate) unsafe fn as_slice<'a>(self) -> &'a [u8] {
        if self.ptr.is_null() || self.len == 0 {
            &[]
        } else {
            slice::from_raw_parts(self.ptr, self.len)
        }
    }

    /// Borrow the foreign bytes as str for the duration of the call (Java keeps ownership and frees them).
    #[inline]
    pub(crate) unsafe fn as_str<'a>(self) -> &'a str {
        if self.ptr.is_null() || self.len == 0 {
            ""
        } else {
            str::from_utf8_unchecked(slice::from_raw_parts(self.ptr, self.len))
        }
    }
}

/// Rust-allocator memory (`alloc_buffer` / `leak_buffer`) crossing the boundary, owned by Rust.
/// Either transferred **in** — Java filled a buffer it obtained from `alloc_buffer` (Rust's
/// allocator) and Rust re-owns it here (`take`/`take_string`) and frees it — or returned **out**:
/// Rust leaks it and the caller frees it via `free_buffer`. `ptr` is null / `len` is 0 for the
/// empty slice. Contrast `ForeignSlice`, which is borrowed Java-`Arena` memory Rust must never free.
#[repr(C)]
pub struct Slice {
    ptr: *const u8,
    len: usize,
}

impl Slice {
    pub(crate) const EMPTY: Slice = Slice {
        ptr: std::ptr::null(),
        len: 0,
    };

    pub(crate) fn is_null(&self) -> bool {
        self.ptr.is_null()
    }

    /// Leak an owned buffer into a `Slice` the caller frees via `free_buffer`.
    #[inline]
    pub(crate) fn from_vec(v: Vec<u8>) -> Self {
        if v.is_empty() {
            return Self::EMPTY;
        }
        let len = v.len();
        let ptr = Box::into_raw(v.into_boxed_slice()) as *mut u8;
        Slice { ptr, len }
    }

    /// Leak an owned buffer into a `Slice` the caller frees via `free_buffer`.
    #[inline]
    pub(crate) fn from_bytes(b: Bytes) -> Self {
        Self::from_vec(Vec::from(b))
    }

    /// Leak an owned buffer into a `Slice` the caller frees via `free_buffer`.
    #[inline]
    pub(crate) fn from_string(s: String) -> Self {
        Self::from_vec(s.into_bytes())
    }

    /// Re-take ownership of a transferred-in payload (Java filled an `alloc_buffer` buffer) as
    /// `Bytes`, zero-copy. The caller must not touch the buffer after the call.
    #[inline]
    pub(crate) unsafe fn take(self) -> Bytes {
        let ptr = self.ptr as *mut u8;
        let len = self.len;
        if ptr.is_null() || len == 0 {
            Bytes::new()
        } else {
            Bytes::from_owner(NativeInput { ptr, len })
        }
    }

    /// Like `take`, but re-owns the payload as a `String` (UTF-8 unchecked — Java already encoded it).
    #[inline]
    pub(crate) unsafe fn take_string(self) -> String {
        let ptr = self.ptr as *mut u8;
        let len = self.len;
        if ptr.is_null() || len == 0 {
            String::new()
        } else {
            // SAFETY: reconstruct the exact `Vec` leaked by `alloc_buffer` (cap == len) and wrap it as a
            // `String`; the bytes are valid UTF-8 by construction on the Java side.
            String::from_utf8_unchecked(Vec::from_raw_parts(ptr, len, len))
        }
    }
}

/// Owner for a Java-handed, `alloc_buffer`-allocated input buffer wrapped zero-copy as `Bytes`
/// (`Bytes::from_owner`). The core retains the `Bytes` (its protocol decoder slices payloads out of
/// it, the journal holds command/state values), so the buffer must outlive the call; it is freed
/// here when the last `Bytes` referencing it drops — at the latest when the `CoreVM` is dropped by
/// `vm_free`.
struct NativeInput {
    ptr: *mut u8,
    len: usize,
}

// SAFETY: the buffer is exclusively owned by this `NativeInput` (the caller relinquishes it on the
// call) and only ever read through `as_ref`, so moving it across threads is sound.
unsafe impl Send for NativeInput {}

impl AsRef<[u8]> for NativeInput {
    #[inline]
    fn as_ref(&self) -> &[u8] {
        // SAFETY: `ptr`/`len` describe the `alloc_buffer` allocation, fully initialized by the
        // caller before hand-off.
        unsafe { slice::from_raw_parts(self.ptr, self.len) }
    }
}

impl Drop for NativeInput {
    fn drop(&mut self) {
        // SAFETY: reconstruct the exact `Vec` leaked by `alloc_buffer` (cap == len) and drop it.
        unsafe { drop(Vec::from_raw_parts(self.ptr, self.len, self.len)) };
    }
}
