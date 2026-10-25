/* Private compatibility surface for the pinned QuickJS-KT sources only.
 * Not a general POSIX implementation. CRITICAL_SECTION preserves recursive
 * JNI callbacks; INIT_ONCE also supports QuickJS's static mutex initializers. */
#ifndef NUVIO_QUICKJS_PTHREAD_H
#define NUVIO_QUICKJS_PTHREAD_H
#include <windows.h>
#include <time.h>
typedef struct { INIT_ONCE once; CRITICAL_SECTION cs; } pthread_mutex_t;
typedef int pthread_mutexattr_t;
typedef CONDITION_VARIABLE pthread_cond_t;
#define PTHREAD_MUTEX_INITIALIZER { INIT_ONCE_STATIC_INIT, {0} }
#define PTHREAD_MUTEX_RECURSIVE 1
int pthread_mutexattr_init(pthread_mutexattr_t *attr);
int pthread_mutexattr_settype(pthread_mutexattr_t *attr, int type);
int pthread_mutexattr_destroy(pthread_mutexattr_t *attr);
int pthread_mutex_init(pthread_mutex_t *mutex, const pthread_mutexattr_t *attr);
int pthread_mutex_destroy(pthread_mutex_t *mutex);
int pthread_mutex_lock(pthread_mutex_t *mutex);
int pthread_mutex_unlock(pthread_mutex_t *mutex);
int pthread_cond_init(pthread_cond_t *cond, const void *attr);
int pthread_cond_destroy(pthread_cond_t *cond);
int pthread_cond_wait(pthread_cond_t *cond, pthread_mutex_t *mutex);
int pthread_cond_timedwait(pthread_cond_t *cond, pthread_mutex_t *mutex, const struct timespec *deadline);
int pthread_cond_signal(pthread_cond_t *cond);
#define CLOCK_REALTIME 0
int clock_gettime(int clock, struct timespec *value);
#endif
