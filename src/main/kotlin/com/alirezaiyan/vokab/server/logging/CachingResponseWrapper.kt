package com.alirezaiyan.vokab.server.logging

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintWriter

class CachingResponseWrapper(response: HttpServletResponse) : HttpServletResponseWrapper(response) {
    
    private val cachedOutputStream = ByteArrayOutputStream()
    private var writer: PrintWriter? = null
    
    override fun getOutputStream(): ServletOutputStream {
        return CachedBodyServletOutputStream(cachedOutputStream)
    }
    
    override fun getWriter(): PrintWriter {
        return writer ?: PrintWriter(cachedOutputStream).also { writer = it }
    }
    
    fun getCachedBody(): ByteArray {
        writer?.flush()
        return cachedOutputStream.toByteArray()
    }
    
    private inner class CachedBodyServletOutputStream(
        private val outputStream: ByteArrayOutputStream
    ) : ServletOutputStream() {
        
        private val originalOutputStream = response.outputStream
        
        @Throws(IOException::class)
        override fun write(b: Int) {
            outputStream.write(b)
            originalOutputStream.write(b)
        }
        
        @Throws(IOException::class)
        override fun write(b: ByteArray) {
            outputStream.write(b)
            originalOutputStream.write(b)
        }
        
        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            outputStream.write(b, off, len)
            originalOutputStream.write(b, off, len)
        }
        
        override fun isReady(): Boolean {
            return originalOutputStream.isReady
        }
        
        override fun setWriteListener(writeListener: WriteListener) {
            originalOutputStream.setWriteListener(writeListener)
        }
        
        @Throws(IOException::class)
        override fun flush() {
            outputStream.flush()
            originalOutputStream.flush()
        }
        
        @Throws(IOException::class)
        override fun close() {
            outputStream.close()
            originalOutputStream.close()
        }
    }
}
