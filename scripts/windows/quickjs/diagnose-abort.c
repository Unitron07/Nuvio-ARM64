#include <stdio.h>
#include <windows.h>
__declspec(noreturn) void nuvio_diagnose_abort(const char *file, int line) {
    fprintf(stderr, "QuickJS native abort at %s:%d\n", file, line);
    fflush(stderr);
    ExitProcess(99);
}
