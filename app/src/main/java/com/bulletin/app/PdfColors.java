package com.bulletin.app;

/** A single display transform: invert first, then monochrome sepia. */
final class PdfColors {
    static float[] matrix(boolean invert, boolean sepia) {
        float[] result = new float[20];
        float[] luminance = {.2126f, .7152f, .0722f};
        float[] tint = {1f, .89f, .70f};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++)
                result[row * 5 + col] = (invert ? -1 : 1)
                    * (sepia ? luminance[col] * tint[row] : row == col ? 1 : 0);
            result[row * 5 + 4] = invert ? 255 * (sepia ? tint[row] : 1) : 0;
        }
        result[18] = 1;
        return result;
    }
}
