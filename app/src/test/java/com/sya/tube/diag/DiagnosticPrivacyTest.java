package com.sya.tube.diag;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticPrivacyTest {
 @Test public void removesUrlsAndTheirCredentials() {
  assertEquals("failed [url]", DiagnosticPrivacy.clean("failed https://user:pass@host/video?token=secret"));
 }
 @Test public void removesStandaloneTokens() {
  assertEquals("token=[redacted] password=[redacted]", DiagnosticPrivacy.clean("token=secret password=hunter2"));
 }
 @Test public void removesWholeAuthorizationHeaderAndQuotedValues() {
  assertEquals("Authorization: [redacted]", DiagnosticPrivacy.clean("Authorization: Bearer secret"));
  assertEquals("password=[redacted]", DiagnosticPrivacy.clean("password=\"two words\""));
 }
 @Test public void keepsUsefulEvents() {
  assertEquals("fullscreen stalled sdk 36", DiagnosticPrivacy.clean("fullscreen stalled sdk 36"));
 }
}
