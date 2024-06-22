%module anitorrent
%{
#include "anitorrent.h"

%}

%include <std_string.i>  // Include support for std::string
%include <std_vector.i>  // Include support for std::vector

%include "include/anitorrent.h"
