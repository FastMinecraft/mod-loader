package dev.fastmc.loader.core;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class UnsafeUtil {
    public final static Unsafe UNSAFE;
    public final static MethodHandles.Lookup TRUSTED_LOOKUP;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);

            Field trustedLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            TRUSTED_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(
                UNSAFE.staticFieldBase(trustedLookupField),
                UNSAFE.staticFieldOffset(trustedLookupField)
            );
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
