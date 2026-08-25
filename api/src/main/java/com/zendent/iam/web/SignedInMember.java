package com.zendent.iam.web;

/**
 * What the Clinic calls the person whose session this is, and what it calls
 * itself. The two names a screen can put in front of them.
 */
public record SignedInMember(String memberName, String clinicName) {
}
