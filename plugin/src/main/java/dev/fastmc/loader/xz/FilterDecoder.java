/*
 * FilterDecoder
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package dev.fastmc.loader.xz;

import java.io.InputStream;

interface FilterDecoder extends FilterCoder {
    int getMemoryUsage();
    InputStream getInputStream(InputStream in, ArrayCache arrayCache);
}
