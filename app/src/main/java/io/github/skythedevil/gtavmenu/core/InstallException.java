package io.github.skythedevil.gtavmenu.core;

/** Any problem we want to show to the person in plain words. */
public class InstallException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public InstallException(String message) { super(message); }
    public InstallException(String message, Throwable cause) { super(message, cause); }
}
