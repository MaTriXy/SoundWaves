package org.bottiger.podcast;

public class ApplicationConfiguration {
	
	public static final boolean DEBUGGING = false;
	
	/** Trace the startup of the app */
	public static final boolean TRACE_STARTUP = false;
	
	/** Copy the database to the SD card */
	public static final boolean COPY_DATABASE = false;
	
	public static final boolean USE_PICASSO = true;
	public static final boolean COLOR_BACKGROUND = false;

    public static final String showListenedKey = "show_listened";

    public static final String formUriBasicAuthLogin = "soundwaves2";
    public static final String formUriBasicAuthPassword = "";

    // Web server certificate
    public static final String CERTIFICATE_HOSTNAME = "soundwavesapp.com";
    public static final String CERTIFICATE_PIN_SHA1 = "";

    // AudioSear.ch
    public static final String AUDIOSEARCH_APP_ID = "";
    public static final String AUDIOSEARCH_SECRET = "";
    public static final String AUDIOSEARCH_CALLBACK = "";

    // package name
    public static final String packageName = "org.bottiger.soundwaves";

    // FOSS flavor
    public static final String ACRA_MAIL = "soundwavesbugs@gmail.com";

    // Google Analytics
    public static final String ANALYTICS_ID = "UA-59611883-1";

    // Amazon Analytics
    public static final String AMAZON_APP_ID = "";
    public static final String AMAZON_AMAZON_AWS_ACCOUNT = "";
    public static final String AMAZON_COGNITO_IDENTITY_POOL = "";
    public static final String AMAZON_UNAUTHENTICATED_ARN = "";
    public static final String AMAZON_AUTHENTICATED_ARN = "";

}
