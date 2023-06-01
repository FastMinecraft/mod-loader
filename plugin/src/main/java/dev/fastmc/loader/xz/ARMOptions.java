/*
 * ARMOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package dev.fastmc.loader.xz;

import java.io.InputStream;
import dev.fastmc.loader.xz.simple.ARM;

/**
 * BCJ filter for little endian ARM instructions.
 */
public class ARMOptions extends BCJOptions {
    private static final int ALIGNMENT = 4;

    public ARMOptions() {
        super(ALIGNMENT);
    }

    public FinishableOutputStream getOutputStream(FinishableOutputStream out,
                                                  ArrayCache arrayCache) {
        return new SimpleOutputStream(out, new ARM(true, startOffset));
    }

    public InputStream getInputStream(InputStream in, ArrayCache arrayCache) {
        return new SimpleInputStream(in, new ARM(false, startOffset));
    }

    FilterEncoder getFilterEncoder() {
        return new BCJEncoder(this, BCJCoder.ARM_FILTER_ID);
    }
}
