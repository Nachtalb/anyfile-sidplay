# SPDX-License-Identifier: GPL-2.0-or-later
cmake_minimum_required(VERSION 3.22.1)
project(anyfile_sidplay LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- libresidfp 1.2.3 + libsidplayfp 3.1.2 (vendored source, GPL v2) ---------------
# Autotools-generated headers (config.h, sidversion.h, siddefs-fp.h) are committed
# pre-substituted, so no configure step runs here. exSID/USBSID/SIDLite builders are
# left out: only the reSIDfp emulation is built.
set(RESIDFP ${CMAKE_CURRENT_SOURCE_DIR}/libresidfp/src)
set(SIDPLAY ${CMAKE_CURRENT_SOURCE_DIR}/libsidplayfp/src)

file(GLOB RESIDFP_SOURCES ${RESIDFP}/*.cpp ${RESIDFP}/resample/*.cpp ${RESIDFP}/residfp/*.cpp)
file(GLOB_RECURSE SIDPLAY_SOURCES ${SIDPLAY}/*.cpp)
list(FILTER SIDPLAY_SOURCES EXCLUDE REGEX ".*/(exsid|usbsid|sidlite)-builder/.*")

add_library(sidplayfp STATIC ${RESIDFP_SOURCES} ${RESIDFP}/version.cc ${SIDPLAY_SOURCES})
target_include_directories(sidplayfp PUBLIC
        ${RESIDFP}
        ${SIDPLAY}
        ${SIDPLAY}/sidtune
        ${SIDPLAY}/builders/residfp-builder)
target_compile_definitions(sidplayfp PRIVATE HAVE_CONFIG_H=1 NDEBUG)
target_compile_options(sidplayfp PRIVATE -O2 -fPIC -w)

add_library(sidcore STATIC sid_core.cpp)
target_include_directories(sidcore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(sidcore PUBLIC sidplayfp)

if (ANDROID)
    add_library(sidjni SHARED sid_jni.cpp)
    find_library(log-lib log)
    target_link_libraries(sidjni sidcore ${log-lib})
else ()
    # Host harness: renders a tune to WAV so the native core can be checked without a device.
    add_executable(sidrender ${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/sidrender.cpp)
    target_link_libraries(sidrender sidcore)
endif ()
