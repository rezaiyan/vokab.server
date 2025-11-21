package com.alirezaiyan.vokab.server.logging

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader

class CachingRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    
    private val cachedBody: ByteArray
    
    init {
        val inputStream = request.inputStream
        cachedBody = inputStream.readAllBytes()
    }
    
    override fun getInputStream(): ServletInputStream {
        return CachedBodyServletInputStream(cachedBody)
    }
    
    override fun getReader(): BufferedReader {
        return BufferedReader(InputStreamReader(inputStream, characterEncoding))
    }
    
    fun getCachedBody(): ByteArray {
        return cachedBody
    }
    
    private class CachedBodyServletInputStream(private val cachedBody: ByteArray) : ServletInputStream() {
        private var inputStream: ByteArrayInputStream = ByteArrayInputStream(cachedBody)
        
        @Throws(IOException::class)
        override fun read(): Int {
            return inputStream.read()
        }
        
        override fun isFinished(): Boolean {
            return inputStream.available() == 0
        }
        
        override fun isReady(): Boolean {
            return true
        }
        
        override fun setReadListener(readListener: ReadListener) {
            throw UnsupportedOperationException("ReadListener not supported")
        }
    }
}
