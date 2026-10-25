#include <stdlib.h>
__declspec(noreturn) void nuvio_diagnose_abort(const char *file, int line);
#define abort() nuvio_diagnose_abort(__FILE__, __LINE__)
