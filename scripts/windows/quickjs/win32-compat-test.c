#include "pthread.h"
#include "sys/time.h"
#include <errno.h>
#include <stdio.h>

#define CHECK(expr) do { if (!(expr)) { fprintf(stderr, "Failed line %d: %s\n", __LINE__, #expr); return 1; } } while (0)
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t condition;
static int ready;
static DWORD WINAPI signal_from_thread(void *unused) {
    (void)unused;
    pthread_mutex_lock(&mutex);
    ready = 1;
    pthread_cond_signal(&condition);
    pthread_mutex_unlock(&mutex);
    return 0;
}
int main(void) {
    pthread_mutexattr_t attr;
    pthread_mutex_t recursive;
    CHECK(pthread_mutexattr_init(&attr) == 0);
    CHECK(pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE) == 0);
    CHECK(pthread_mutex_init(&recursive, &attr) == 0);
    CHECK(pthread_mutexattr_destroy(&attr) == 0);
    CHECK(pthread_mutex_lock(&recursive) == 0);
    CHECK(pthread_mutex_lock(&recursive) == 0);
    CHECK(pthread_mutex_unlock(&recursive) == 0);
    CHECK(pthread_mutex_unlock(&recursive) == 0);
    CHECK(pthread_mutex_destroy(&recursive) == 0);
    CHECK(pthread_cond_init(&condition, NULL) == 0);
    CHECK(pthread_mutex_lock(&mutex) == 0);
    struct timespec deadline;
    CHECK(clock_gettime(CLOCK_REALTIME, &deadline) == 0);
    CHECK(pthread_cond_timedwait(&condition, &mutex, &deadline) == ETIMEDOUT);
    HANDLE thread = CreateThread(NULL, 0, signal_from_thread, NULL, 0, NULL);
    CHECK(thread != NULL);
    while (!ready) CHECK(pthread_cond_wait(&condition, &mutex) == 0);
    CHECK(pthread_mutex_unlock(&mutex) == 0);
    CHECK(WaitForSingleObject(thread, 5000) == WAIT_OBJECT_0);
    CloseHandle(thread);
    CHECK(pthread_cond_destroy(&condition) == 0);
    CHECK(pthread_mutex_destroy(&mutex) == 0);
    struct timeval tv;
    CHECK(gettimeofday(&tv, NULL) == 0);
    CHECK(tv.tv_sec > 0 && tv.tv_usec >= 0 && tv.tv_usec < 1000000);
    puts("PASS: static/recursive mutexes, condition timeout/wakeup, realtime clocks");
    return 0;
}
