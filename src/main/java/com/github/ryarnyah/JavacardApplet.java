package com.github.ryarnyah;

import java.util.Objects;

public class JavacardApplet {
    private String appletClass;
    private String packageName;
    private String packageAID;
    private String appletAID;
    private String outputName;

    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppletClass() {
        return appletClass;
    }

    public void setAppletClass(String appletClass) {
        this.appletClass = appletClass;
    }

    public String getPackageAID() {
        return packageAID;
    }

    public void setPackageAID(String packageAID) {
        this.packageAID = packageAID;
    }

    public String getAppletAID() {
        return appletAID;
    }

    public void setAppletAID(String appletAID) {
        this.appletAID = appletAID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavacardApplet that = (JavacardApplet) o;
        return Objects.equals(appletClass, that.appletClass)
                && Objects.equals(packageName, that.packageName)
                && Objects.equals(packageAID, that.packageAID)
                && Objects.equals(appletAID, that.appletAID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appletClass, packageName, packageAID, appletAID);
    }

    @Override
    public String toString() {
        return packageName + "." + appletClass;
    }
}
