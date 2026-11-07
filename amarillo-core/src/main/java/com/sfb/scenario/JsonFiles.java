package com.sfb.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Writing one of this package's JSON files so that a failure cannot leave a half-written one.
 *
 * <h2>The hazard</h2>
 * Both files this package writes are the player's own irreplaceable data — a saved battle and a
 * saved fleet — and both used to be written with a bare {@code mapper.writeValue(file, this)},
 * straight to the destination. That truncates the existing file the moment it opens, so a crash,
 * a full disk or a killed server partway through leaves a file that is neither the old save nor
 * the new one. The old one is already gone by then.
 *
 * <h2>The shape of the fix</h2>
 * Write to a temporary file beside the destination, re-read it to prove it parses, and only then
 * move it into place. Every failure then lands on the temporary file, and the destination either
 * holds the previous save or the complete new one — never a fragment.
 *
 * <p>Three details that are deliberate:
 * <ul>
 *   <li>The temporary file goes in the SAME DIRECTORY. A move across filesystems is a copy, which
 *       is exactly the non-atomic write this exists to avoid.</li>
 *   <li>The bytes are produced in memory first, so a serialisation failure — a cycle, an
 *       unmappable field — writes nothing at all rather than a prefix. These files are hundreds
 *       of kilobytes, not gigabytes.</li>
 *   <li>{@code fd.sync()} before the move, because a move is only atomic with respect to the
 *       directory entry: without the flush the entry can land while the contents are still in
 *       the OS cache, which is the same truncated file by a longer route.</li>
 * </ul>
 *
 * <h2>What this does NOT promise</h2>
 * The re-read proves the file PARSES, not that it is the battle that was in memory — that is what
 * the round-trip tests are for. And a device that lies about {@code sync} can still lose it. This
 * closes the ordinary failure, not every failure.
 */
final class JsonFiles {

    private JsonFiles() {
    }

    /**
     * Write {@code value} to {@code dest} as JSON, atomically.
     *
     * @param reread the type to parse the temporary file back as, to prove it is readable before
     *               anything replaces the destination
     */
    static void writeAtomically(ObjectMapper mapper, File dest, Object value, Class<?> reread)
            throws IOException {

        byte[] bytes = mapper.writeValueAsBytes(value);

        File dir = dest.getAbsoluteFile().getParentFile();
        File tmp = File.createTempFile(dest.getName() + "-", ".tmp", dir);
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }

            // Proves the file on disk is readable, which a short write would not be.
            mapper.readValue(tmp, reread);

            try {
                Files.move(tmp.toPath(), dest.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems cannot promise it. A plain replace is still far better than
                // writing the destination in place, so fall back rather than failing the save.
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // Present only if something above threw; the move consumed it otherwise.
            if (tmp.exists() && !tmp.delete())
                tmp.deleteOnExit();
        }
    }
}
