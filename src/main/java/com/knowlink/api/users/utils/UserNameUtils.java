package com.knowlink.api.users.utils;

import com.knowlink.api.users.data.models.User;

public final class UserNameUtils {

    /** Mask applied to the identity of accounts that are no longer active (US-04). */
    public static final String DEACTIVATED_DISPLAY_NAME = "Usuario desactivado";

    /** Mask applied to the identity of permanently deleted accounts (US-24). */
    public static final String DELETED_DISPLAY_NAME = "Usuario eliminado";

    private UserNameUtils() {}

    /** "first last" normalized with trim, without surrounding double spaces. */
    public static String buildFullName(String firstName, String lastName) {
        return (firstName.trim() + " " + lastName.trim()).trim();
    }

    public static String firstNameOf(User user) {
        if (hasStructuredName(user)) {
            return user.getFirstName().trim();
        }
        String fullName = user.getFullName() == null ? "" : user.getFullName().trim();
        int idx = fullName.indexOf(' ');
        return idx < 0 ? fullName : fullName.substring(0, idx);
    }

    public static String lastNameOf(User user) {
        if (hasStructuredName(user)) {
            return user.getLastName().trim();
        }
        String fullName = user.getFullName() == null ? "" : user.getFullName().trim();
        int idx = fullName.indexOf(' ');
        return idx < 0 ? "" : fullName.substring(idx + 1).trim();
    }

    private static boolean hasStructuredName(User user) {
        return user.getFirstName() != null && !user.getFirstName().isBlank()
                && user.getLastName() != null && !user.getLastName().isBlank();
    }
}
