package com.vitals.lib

class VitalsNativeException(var errCode: String, msg: String): Exception(msg)