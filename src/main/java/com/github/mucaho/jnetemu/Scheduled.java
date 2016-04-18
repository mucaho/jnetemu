/*
 * The MIT License (MIT)
 * Copyright (c) 2013-2016 mucaho (https://github.com/mucaho)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.github.mucaho.jnetemu;

/**
 * Interface for handling scheduled objects, that should be processed at a given time.
 * <br>
 * The time is represented as the difference in {@code ms},
 * between the time this object is ready to be processed and midnight, 01.01.1970 UTC.
 */
public interface Scheduled extends Comparable<Scheduled> {
    /**
     * Retrieve the time this object will be ready to be processed.
     * @return the difference in {@code ms}, between the time this object is ready to be processed
     *         and midnight, 01.01.1970 UTC.
     */
    long getScheduledTime();

    /**
     * Check whether this object is ready to be processed.
     * @param timeNow the current low-resolution system time in {@code ms},
     *                which will be used to determine if this object is ready yet.
     *                It represents the difference between the current time
     *                and midnight, 01.01.1970 UTC.
     * @return {@code true}, iff object is ready to be processed, {@code false} otherwise
     */
    boolean isReady(long timeNow);
}
