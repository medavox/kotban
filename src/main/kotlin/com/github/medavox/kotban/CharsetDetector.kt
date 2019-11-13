package com.github.medavox.kotban

/*
 *  Copyright 2010 Georgios Migdos .
 *
 *  Licensed under the Apache License, Version 2.0 (the "License")
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder

/**
 * from https://gmigdos.wordpress.com/2010/04/08/java-how-to-auto-detect-a-files-encoding/
 * @author Georgios Migdos
 */
object CharsetDetector {

    fun detectCharset(f:File):Charset? {
        val charsets = Charset.availableCharsets()
        return charsets.values.map{detectCharset(f, it)}.firstOrNull{it != null}
    }

    private fun detectCharset(f:File, charset:Charset):Charset? {
        try {
            val input:BufferedInputStream = BufferedInputStream(FileInputStream(f))

            val decoder:CharsetDecoder = charset.newDecoder()
            decoder.reset()

            val buffer:ByteArray = ByteArray(512)
            var identified = false
            while ((input.read(buffer) != -1) && (!identified)) {
                identified = identify(buffer, decoder)
            }

            input.close()

            if (identified) {
                return charset
            } else {
                return null
            }

        } catch (e:Exception) {
            return null
        }
    }

    private fun identify(bytes:ByteArray, decoder:CharsetDecoder):Boolean {
        try {
            decoder.decode(ByteBuffer.wrap(bytes))
        } catch (e:CharacterCodingException) {
            return false
        }
        return true
    }

    fun vow(f:File) {
        val charset:Charset? = CharsetDetector.detectCharset(f)

        if (charset != null) {
            val reader = InputStreamReader (FileInputStream(f), charset)

        } else {
            System.out.println("Unrecognized charset.")
        }
    }
}