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

# 查找当前目录下的所有源文件
# 并将名称保存到 DIR_SRCS 变量
aux_source_directory(src/main/jni/source DIR_SRCS)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
#   message("******* arm64-v8a *********")
   list(REMOVE_ITEM DIR_SRCS
       src/main/jni/source/compare_neon.cc
       src/main/jni/source/rotate_neon.cc
       src/main/jni/source/row_neon.cc
       src/main/jni/source/scale_neon.cc
       src/main/jni/source/compare_gcc.cc
       src/main/jni/source/rotate_gcc.cc
       src/main/jni/source/row_gcc.cc
       src/main/jni/source/scale_gcc.cc )
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
       list(REMOVE_ITEM DIR_SRCS
           src/main/jni/source/compare_neon64.cc
           src/main/jni/source/rotate_neon64.cc
           src/main/jni/source/row_neon64.cc
           src/main/jni/source/scale_neon64.cc
           src/main/jni/source/compare_gcc.cc
           src/main/jni/source/rotate_gcc.cc
           src/main/jni/source/row_gcc.cc
           src/main/jni/source/scale_gcc.cc )
#       message("******* armeabi-v7a *********")
else()
       list(REMOVE_ITEM DIR_SRCS
           src/main/jni/source/compare_neon64.cc
           src/main/jni/source/rotate_neon64.cc
           src/main/jni/source/row_neon64.cc
           src/main/jni/source/scale_neon64.cc
           src/main/jni/source/compare_neon.cc
           src/main/jni/source/rotate_neon.cc
           src/main/jni/source/row_neon.cc
           src/main/jni/source/scale_neon.cc )
#       message("******* others *********")
endif()

# foreach(f ${DIR_SRCS})
#  message(${f})
# endforeach()

add_library( yuvtool
             SHARED
             src/main/jni/yuvtool.c
             ${DIR_SRCS} )

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

# find_library( jnigraphics-lib
#              jnigraphics )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
target_link_libraries( # Specifies the target library.
                       yuvtool
                       -landroid
                       # Links the target library to the log library
                       # included in the NDK.
                       # ${jnigraphics-lib}
                       ${log-lib} )