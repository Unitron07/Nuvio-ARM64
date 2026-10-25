cmake_minimum_required(VERSION 3.25)
project(nuvio_quickjs_jni LANGUAGES C)

# Bellard QuickJS uses GNU C extensions. Clang targets the Microsoft ABI and
# links the Windows SDK/UCRT; this is not a MinGW or MSYS2 runtime build.
if(NOT WIN32 OR NOT CMAKE_C_COMPILER_ID STREQUAL "Clang" OR NOT MSVC)
  message(FATAL_ERROR "Use clang-cl with the MSVC ARM64 (or x64 regression) toolchain")
endif()
if(NOT DEFINED QUICKJS_KT_SOURCE OR NOT DEFINED ENV{JAVA_HOME})
  message(FATAL_ERROR "QUICKJS_KT_SOURCE and JAVA_HOME are required")
endif()
set(native "${QUICKJS_KT_SOURCE}/quickjs/native")
file(READ "${native}/quickjs/VERSION" version)
string(STRIP "${version}" version)
file(GLOB_RECURSE jni CONFIGURE_DEPENDS "${native}/jni/*.c")
add_library(quickjs SHARED ${jni}
  "${native}/quickjs/quickjs.c" "${native}/quickjs/cutils.c"
  "${native}/quickjs/dtoa.c" "${native}/quickjs/libregexp.c"
  "${native}/quickjs/libunicode.c" "${native}/common/quickjs_version.c"
  "${native}/common/quickjs_interrupt.c" win32-compat.c)
set_target_properties(quickjs PROPERTIES PREFIX "lib" C_STANDARD 11
  MSVC_RUNTIME_LIBRARY "MultiThreaded")
target_include_directories(quickjs PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include"
  "${native}/quickjs" "${native}/common" "${native}/c-vector"
  "${native}/jni" "${native}/jni/mapping"
  "$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/win32")
target_compile_definitions(quickjs PRIVATE CONFIG_WIN32 CONFIG_BIGNUM
  CONFIG_VERSION="${version}" _CRT_SECURE_NO_WARNINGS WIN32_LEAN_AND_MEAN NOMINMAX)
target_compile_options(quickjs PRIVATE /clang:-std=gnu11 /FIstdlib.h /FImalloc.h)
# JNIEXPORT supplies the public JNI symbols. Do not use the upstream .def,
# which exports the generic C API for Kotlin/Native rather than the JNI ABI.
target_link_options(quickjs PRIVATE /INCREMENTAL:NO /Brepro)
