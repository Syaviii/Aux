package com.sya.tube.update;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReleaseVersionTest {
 @Test public void handlesLargeRemoteVersionsWithoutCrashing() {
  assertTrue(ReleaseVersion.compare("99999999999999999999.1", "1.0.5") > 0);
 }
 @Test public void dottedVersionsCompareNumerically() {
  assertTrue(ReleaseVersion.compare("1.10.0", "1.9.9") > 0);
  assertEquals(0, ReleaseVersion.compare("1.0", "1.0.0"));
 }
 @Test public void rejectsUnsafeReleaseNames() {
  assertFalse(ReleaseVersion.valid("../../file"));
  assertFalse(ReleaseVersion.valid("1.0-beta"));
  assertTrue(ReleaseVersion.valid("1.0.6"));
 }
}
