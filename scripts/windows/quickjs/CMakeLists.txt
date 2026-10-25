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
file(TO_CMAKE_PATH "${QUICKJS_KT_SOURCE}/quickjs/native" native)
file(TO_CMAKE_PATH "$ENV{JAVA_HOME}" java_home)
file(READ "${native}/quickjs/VERSION" version)
string(STRIP "${version}" version)
file(GLOB_RECURSE jni CONFIGURE_DEPENDS "${native}/jni/*.c")
# MSVC-mode Clang 22 treats enum bitfields as signed. Values 4..7 of this
# three-bit field then read back negative and abort when resolving globals.
# Patch only a build copy; the pinned source checkout remains unmodified.
file(READ "${native}/quickjs/quickjs.c" engine_source)
set(closure_field "JSClosureTypeEnum closure_type : 3;")
string(FIND "${engine_source}" "${closure_field}" closure_field_offset)
if(closure_field_offset EQUAL -1)
  message(FATAL_ERROR "Pinned QuickJS closure field changed; review the MSVC port")
endif()
string(REPLACE "${closure_field}" "unsigned int closure_type : 3;" engine_source "${engine_source}")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/quickjs-msvc.c" "${engine_source}")
add_library(quickjs SHARED ${jni}
  "${CMAKE_CURRENT_BINARY_DIR}/quickjs-msvc.c" "${native}/quickjs/cutils.c"
  "${native}/quickjs/dtoa.c" "${native}/quickjs/libregexp.c"
  "${native}/quickjs/libunicode.c" "${native}/common/quickjs_version.c"
  "${native}/common/quickjs_interrupt.c" win32-compat.c wide-integer.c)
set_target_properties(quickjs PROPERTIES PREFIX "lib" C_STANDARD 11
  MSVC_RUNTIME_LIBRARY "MultiThreaded")
target_include_directories(quickjs PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include"
  "${native}/quickjs" "${native}/common" "${native}/c-vector"
  "${native}/jni" "${native}/jni/mapping"
  "${java_home}/include" "${java_home}/include/win32")
target_compile_definitions(quickjs PRIVATE CONFIG_WIN32 CONFIG_BIGNUM
  CONFIG_VERSION="${version}" _CRT_SECURE_NO_WARNINGS WIN32_LEAN_AND_MEAN NOMINMAX)
# Keep the engine's native 64-bit BigInt limbs. The small wide-integer helper
# supplies Clang's unsigned division builtins absent from the MSVC runtime.
# Match Bellard's Makefile: the engine requires wrapping signed arithmetic.
target_compile_options(quickjs PRIVATE /clang:-std=gnu11 /clang:-fwrapv /FIstdlib.h /FImalloc.h)
# JNIEXPORT supplies the public JNI symbols. Do not use the upstream .def,
# which exports the generic C API for Kotlin/Native rather than the JNI ABI.
target_link_options(quickjs PRIVATE /INCREMENTAL:NO /Brepro)

enable_testing()
add_executable(win32-compat-test win32-compat-test.c win32-compat.c)
target_include_directories(win32-compat-test PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_definitions(win32-compat-test PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
set_target_properties(win32-compat-test PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded")
add_test(NAME win32-synchronization COMMAND win32-compat-test)
set_tests_properties(win32-synchronization PROPERTIES TIMEOUT 15)
add_executable(wide-integer-test wide-integer-test.c wide-integer.c)
set_target_properties(wide-integer-test PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded")
add_test(NAME wide-integer-division COMMAND wide-integer-test)
set_tests_properties(wide-integer-division PROPERTIES TIMEOUT 15)
