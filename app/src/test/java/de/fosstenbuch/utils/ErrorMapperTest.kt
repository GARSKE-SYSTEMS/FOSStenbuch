package de.fosstenbuch.utils

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `SQLiteConstraintException maps to conflict message`() {
        val message = ErrorMapper.map(SQLiteConstraintException("UNIQUE constraint failed"))
        assertTrue(message.contains("Datenkonflikt"))
    }

    @Test
    fun `SQLiteFullException maps to storage message`() {
        val message = ErrorMapper.map(SQLiteFullException())
        assertTrue(message.contains("Speicher voll"))
    }

    @Test
    fun `SQLiteException maps to database error`() {
        val message = ErrorMapper.map(SQLiteException("generic SQL error"))
        assertTrue(message.contains("Datenbankfehler"))
    }

    @Test
    fun `UnknownHostException maps to no internet`() {
        val message = ErrorMapper.map(UnknownHostException("Unable to resolve host"))
        assertTrue(message.contains("Internetverbindung"))
    }

    @Test
    fun `SocketTimeoutException maps to timeout`() {
        val message = ErrorMapper.map(SocketTimeoutException("Read timed out"))
        assertTrue(message.contains("Zeitüberschreitung"))
    }

    @Test
    fun `FileNotFoundException maps correctly`() {
        val message = ErrorMapper.map(FileNotFoundException("file.txt"))
        assertTrue(message.contains("Datei nicht gefunden"))
    }

    @Test
    fun `generic IOException maps to IO error`() {
        val message = ErrorMapper.map(IOException("disk read error"))
        assertTrue(message.contains("Ausgabefehler"))
    }

    @Test
    fun `IllegalArgumentException includes message`() {
        val message = ErrorMapper.map(IllegalArgumentException("Invalid input"))
        assertTrue(message.contains("Ungültige Eingabe"))
    }

    @Test
    fun `SecurityException maps to permission message`() {
        val message = ErrorMapper.map(SecurityException("Permission denied"))
        assertTrue(message.contains("Berechtigung"))
    }

    @Test
    fun `NumberFormatException maps to format error`() {
        val message = ErrorMapper.map(NumberFormatException("For input string: abc"))
        assertTrue(message.contains("Zahlenformat"))
    }

    @Test
    fun `unknown exception returns generic message`() {
        val message = ErrorMapper.map(RuntimeException("something unknown"))
        assertTrue(message.contains("unerwarteter Fehler"))
    }

    @Test
    fun `mapWithContext includes context message`() {
        val message = ErrorMapper.mapWithContext(RuntimeException("error"), "Fahrt speichern")
        assertTrue(message.startsWith("Fahrt speichern:"))
    }
}
