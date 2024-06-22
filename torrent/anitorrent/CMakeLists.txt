cmake_minimum_required(VERSION 3.28)
project(anitorrent)

set(CMAKE_CXX_STANDARD 23)


include(FetchContent)

# Libtorrent
FetchContent_Declare(
        libtorrent
        GIT_REPOSITORY https://github.com/arvidn/libtorrent.git
        GIT_TAG v2.0.10 # You can specify a particular branch, tag, or commit here
)
FetchContent_MakeAvailable(libtorrent)

# JNI
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})


add_library(anitorrent
        SHARED
        include/anitorrent.h
        src/anitorrent.cpp
        src/anitorrent_wrap.cpp
)
target_include_directories(anitorrent PRIVATE include)

add_executable(anitorrent_test
        include/anitorrent.h
        src/anitorrent.cpp
        src/main.cpp
)
target_include_directories(anitorrent_test PRIVATE include)


target_link_libraries(anitorrent PRIVATE torrent-rasterbar ${JNI_LIBRARIES})
