
// Need to add a variable's initialzing instruction to the Read and Reassign instructions.
// How do we make os-specific code not suck?
// How do we pass compiler flags into THE-ProgrammingLanguage?


// How to handle system calls in THE-ProgrammingLanguage:

time_t {
	int seconds
	int milliseconds
}

wintimeThing {
	int hours
	int minutes
	int seconds
	int millis
	int nanos
}

macTime_t {
	long nanotime
	boolean amPM
}

THE_Time_t {
	int seconds
}

THE_Time_t getTime(int timezone) {
	
	THE_Time_t goodTime;
	
	LINKTIME_CHOOSE {
		wintimeThing win_time(int) {
			wintimeThing t = win_time(timezone)
			goodTime = doBunchOfWindowsStuff(t)
			win_time_cleanup(t)
		}
		
		time_t unix_time(int) {
			time_t t = unix_time(timezone)
			goodTime = doBunchOfUNIXStuff(t)
		}
		
		macTime_t macos_time(void) {
			macTime_t t = macos_time()
			goodTime = doBunchOfMACStuff(t)
		}
		
		else {
			print("Error!")
			return
		}
	}
	
	return goodTime;
}







// Variadic arguments:

int x = max(1, 6, 4, 6, 3, 1)

int max(variadic int[] arr)
	...
]

int a = 324
int b = 93
string s = string.new(maxLen: 99)
printToString(s, a, b)

void printToString(string dest, variadic string[] args)
	dest = ""
	for string s : args
		dest += s
	]
	return dest
]








// Named parameters (I'm very nervous about this due to the perils of Swift):
// Maybe named parameters should only be for constructors?

graphics.drawRect(x: 0, y: 0, width: 200, height: 100)

void drawRect(int `x`, int `y`, int `width`, int `height`)
	...
]







// Length of multidimensional arrays:

int[,] arr = int[100, 50]

print(#arr) // Total element count

print(arr.length(0))
print(arr.length(1))
print(arr.length(2))

print(arr.length0)
print(arr.length1)
print(arr.length2)
...

// In C#:
arr.GetLength(2)



// System libraries handling:


// In LLVM IR:

declare i32 @strlen(i8*)   ;; Assume strlen will be linked later.
...
%B = call i32 @strlen(i8* %b)  ;; Call function by name only.



// Examples from C:

static FILE *stbiw__fopen(char const *filename, char const *mode)
{
   FILE *f;
#if defined(_WIN32) && defined(STBIW_WINDOWS_UTF8)
   wchar_t wMode[64];
   wchar_t wFilename[1024];
   if (0 == MultiByteToWideChar(65001 /* UTF8 */, 0, filename, -1, wFilename, sizeof(wFilename)/sizeof(*wFilename)))
      return 0;

   if (0 == MultiByteToWideChar(65001 /* UTF8 */, 0, mode, -1, wMode, sizeof(wMode)/sizeof(*wMode)))
      return 0;

#if defined(_MSC_VER) && _MSC_VER >= 1400
   if (0 != _wfopen_s(&f, wFilename, wMode))
      f = 0;
#else
   f = _wfopen(wFilename, wMode);
#endif

#elif defined(_MSC_VER) && _MSC_VER >= 1400
   if (0 != fopen_s(&f, filename, mode))
      f=0;
#else
   f = fopen(filename, mode);
#endif
   return f;
}



// Examples from Ada:

// GNAT.OS_Lib just maps directly to C functions.

function Write
  (FD   : File_Descriptor;
   A    : System.Address;
   N    : Integer)
   return Integer;
pragma Import (C, Write, "write");



// Examples from V-Lang:


pub fn (mut c UdpConn) write_to_ptr(addr Addr, b &u8, len int) !int {
	res := C.sendto(c.sock.handle, b, len, 0, voidptr(&addr), addr.len())
	if res >= 0 {
		return res
	}
	code := error_code()
	if code == int(error_ewouldblock) {
		c.wait_for_write()!
		socket_error(C.sendto(c.sock.handle, b, len, 0, voidptr(&addr), addr.len()))!
	} else {
		wrap_error(code)!
	}
	return error('none')
}


// Examples from Zig:

const builtin = @import("builtin");

const c = @cImport({
    @cDefine("NDEBUG", builtin.mode == .ReleaseFast);
    if (something) {
        @cDefine("_GNU_SOURCE", {});
    }
    @cInclude("stdlib.h");
    if (something) {
        @cUndef("_GNU_SOURCE");
    }
    @cInclude("soundio.h");
});



const win = @import("std").os.windows;
const unix = @import("std").os.linux;

extern "user32" fn MessageBoxA(?win.HWND, [*:0]const u8, [*:0]const u8, u32) callconv(win.WINAPI) i32;
extern "user32" fn linuxMessageBox(?win.HWND, [*:0]const u8, [*:0]const u8, u32) callconv(win.WINAPI) i32;

pub fn main() !void {
	if (builtin.os == .windows) {
		_ = MessageBoxA(null, "world!", "Hello", 0);
	} else if (buildin.os == .linux) {
		_ = linuxMessageBox(sdlfsdfasdfds...);
	}
}


https://github.com/ziglang/zig/blob/master/lib/std/time.zig


pub fn now() error{Unsupported}!Instant {
	const clock_id = switch (builtin.os.tag) {
		.windows => {
			// QPC on windows doesn't fail on >= XP/2000 and includes time suspended.
			return Instant{ .timestamp = windows.QueryPerformanceCounter() };
		},
		.wasi => {
			var ns: std.os.wasi.timestamp_t = undefined;
			const rc = std.os.wasi.clock_time_get(.MONOTONIC, 1, &ns);
			if (rc != .SUCCESS) return error.Unsupported;
			return .{ .timestamp = ns };
		},
		.uefi => {
			var value: std.os.uefi.Time = undefined;
			const status = std.os.uefi.system_table.runtime_services.getTime(&value, null);
			if (status != .Success) return error.Unsupported;
			return Instant{ .timestamp = value.toEpoch() };
		},
		// On darwin, use UPTIME_RAW instead of MONOTONIC as it ticks while
		// suspended.
		.macos, .ios, .tvos, .watchos, .visionos => posix.CLOCK.UPTIME_RAW,
		// On freebsd derivatives, use MONOTONIC_FAST as currently there's
		// no precision tradeoff.
		.freebsd, .dragonfly => posix.CLOCK.MONOTONIC_FAST,
		// On linux, use BOOTTIME instead of MONOTONIC as it ticks while
		// suspended.
		.linux => posix.CLOCK.BOOTTIME,
		// On other posix systems, MONOTONIC is generally the fastest and
		// ticks while suspended.
		else => posix.CLOCK.MONOTONIC,
	};

	var ts: posix.timespec = undefined;
	posix.clock_gettime(clock_id, &ts) catch return error.Unsupported;
	return .{ .timestamp = ts };
}

// Defined in zig/lib/std/os/wasi.zig:

pub extern "wasi_snapshot_preview1" fn clock_time_get(clock_id: clockid_t, precision: timestamp_t, timestamp: *timestamp_t) errno_t;

// Defined in zig/stage1/wasi.c:

uint32_t wasi_snapshot_preview1_clock_time_get(uint32_t id, uint64_t precision, uint32_t res_timestamp) {
    uint8_t *const m = *wasm_memory;
    (void)precision;
    uint64_t *res_timestamp_ptr = (uint64_t *)&m[res_timestamp];
#if LOG_TRACE
    fprintf(stderr, "wasi_snapshot_preview1_clock_time_get(%u, %llu)\n", id, (unsigned long long)precision);
#endif

    switch (id) {
        case wasi_clockid_realtime:
            *res_timestamp_ptr = time(NULL) * UINT64_C(1000000000);
            break;
        case wasi_clockid_monotonic:
        case wasi_clockid_process_cputime_id:
        case wasi_clockid_thread_cputime_id:
            *res_timestamp_ptr = clock() * (UINT64_C(1000000000) / CLOCKS_PER_SEC);
            break;
        default: return wasi_errno_inval;
    }
    return wasi_errno_success;
}





