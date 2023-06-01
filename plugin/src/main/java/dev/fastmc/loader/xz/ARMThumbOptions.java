/*
 * ARMThumbOptions
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package dev.fastmc.loader.xz;

import java.io.InputStream;
import dev.fastmc.loader.xz.simple.ARMThumb;

/**
 * BCJ filter for little endian ARM-Thumb instructions.
 */
public class ARMThumbOptions extends BCJOptions {
    private static final int ALIGNMENT = 2;

    public ARMThumbOptions() {
        super(ALIGNMENT);
    }

    public FinishableOutputStream getOutputStream(FinishableOutputStream out,
                                                  ArrayCache arrayCache) {
        return new SimpleOutputStream(out, new ARMThumb(true, startOffset));
    }

    public InputStream getInputStream(InputStream in, ArrayCache arrayCache) {
        return new SimpleInputStream(in, new ARMThumb(false, startOffset));
    }

    FilterEncoder getFilterEncoder() {
        return new BCJEncoder(this, BCJCoder.ARMTHUMB_FILTER_ID);
    }
}
