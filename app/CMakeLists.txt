# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

set(distribution_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs)

include_directories(src/main/jni/include/)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add.library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

add_library( yuvtool
             SHARED
             src/main/jni/yuvtool.c
             src/main/jni/source/compare.cc
             src/main/jni/source/compare_common.cc
             src/main/jni/source/compare_gcc.cc
             src/main/jni/source/compare_msa.cc
             src/main/jni/source/compare_neon64.cc
             src/main/jni/source/compare_win.cc
             src/main/jni/source/compare_neon.cc
             src/main/jni/source/convert.cc
             src/main/jni/source/convert_argb.cc
             src/main/jni/source/convert_from.cc
             src/main/jni/source/convert_from_argb.cc
             src/main/jni/source/convert_jpeg.cc
             src/main/jni/source/convert_to_argb.cc
             src/main/jni/source/convert_to_i420.cc
             src/main/jni/source/cpu_id.cc
             src/main/jni/source/mjpeg_decoder.cc
             src/main/jni/source/mjpeg_validate.cc
             src/main/jni/source/planar_functions.cc
             src/main/jni/source/rotate.cc
             src/main/jni/source/rotate_any.cc
             src/main/jni/source/rotate_argb.cc
             src/main/jni/source/rotate_common.cc
             src/main/jni/source/rotate_gcc.cc
             src/main/jni/source/rotate_msa.cc
             src/main/jni/source/rotate_neon64.cc
             src/main/jni/source/rotate_win.cc
             src/main/jni/source/rotate_dspr2.cc
             src/main/jni/source/rotate_neon.cc
             src/main/jni/source/row_any.cc
             src/main/jni/source/row_common.cc
             src/main/jni/source/row_gcc.cc
             src/main/jni/source/row_msa.cc
             src/main/jni/source/row_neon64.cc
             src/main/jni/source/row_win.cc
             src/main/jni/source/row_dspr2.cc
             src/main/jni/source/row_neon.cc
             src/main/jni/source/scale.cc
             src/main/jni/source/scale_any.cc
             src/main/jni/source/scale_argb.cc
             src/main/jni/source/scale_common.cc
             src/main/jni/source/scale_gcc.cc
             src/main/jni/source/scale_msa.cc
             src/main/jni/source/scale_neon64.cc
             src/main/jni/source/scale_win.cc
             src/main/jni/source/scale_dspr2.cc
             src/main/jni/source/scale_neon.cc
             src/main/jni/source/video_common.cc
              )

# add_library( yuv
#              SHARED
#              IMPORTED )

# set_target_properties( yuv
#              PROPERTIES IMPORTED_LOCATION
#              ../../../../src/main/jniLibs/${ANDROID_ABI}/libyuv.so )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib
              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

find_library( jnigraphics-lib
              jnigraphics )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
target_link_libraries( # Specifies the target library.
                       yuvtool
                       -landroid
                       # yuv
                       # Links the target library to the log library
                       # included in the NDK.
                       ${jnigraphics-lib}
                       ${log-lib} )