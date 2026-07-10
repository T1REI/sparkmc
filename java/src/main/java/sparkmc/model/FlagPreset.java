package sparkmc.model;

public enum FlagPreset {
    Aikar,
    Meowlce,
    None,
    Custom;

    public String label() {
        return switch (this) {
            case Aikar -> "Aikar Flags";
            case Meowlce -> "Meowlce Flags";
            case None -> "None";
            case Custom -> "Custom";
        };
    }

    public String[] jvmFlags() {
        return switch (this) {
            case Aikar -> AIKAR;
            case Meowlce -> MEOWLCE;
            case None, Custom -> new String[0];
        };
    }

    public static FlagPreset[] all() {
        return values();
    }

    private static final String[] AIKAR = {
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:G1HeapWastePercent=5",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs",
        "-Daikars.new.flags=true",
        "-XX:G1NewSizePercent=30",
        "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20",
    };

    private static final String[] MEOWLCE = {
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=28",
        "-XX:G1MaxNewSizePercent=50",
        "-XX:G1HeapRegionSize=16M",
        "-XX:G1ReservePercent=15",
        "-XX:G1MixedGCCountTarget=3",
        "-XX:InitiatingHeapOccupancyPercent=20",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:SurvivorRatio=32",
        "-XX:G1HeapWastePercent=5",
        "-XX:MaxTenuringThreshold=1",
        "-XX:+PerfDisableSharedMem",
        "-XX:G1SATBBufferEnqueueingThresholdPercent=30",
        "-XX:G1ConcMarkStepDurationMillis=5",
        "-XX:G1RSetUpdatingPauseTimePercent=0",
        "-XX:+UseNUMA",
        "-XX:-DontCompileHugeMethods",
        "-XX:MaxNodeLimit=240000",
        "-XX:NodeLimitFudgeFactor=8000",
        "-XX:ReservedCodeCacheSize=400M",
        "-XX:NonNMethodCodeHeapSize=12M",
        "-XX:ProfiledCodeHeapSize=194M",
        "-XX:NonProfiledCodeHeapSize=194M",
        "-XX:NmethodSweepActivity=1",
        "-XX:+UseFastUnorderedTimeStamps",
        "-XX:+UseCriticalJavaThreadPriority",
        "-XX:AllocatePrefetchStyle=3",
        "-XX:+AlwaysActAsServerClassMachine",
        "-XX:+UseTransparentHugePages",
        "-XX:LargePageSizeInBytes=2M",
        "-XX:+UseLargePages",
        "-XX:+EagerJVMCI",
        "-XX:+UseStringDeduplication",
        "-XX:+UseAES",
        "-XX:+UseAESIntrinsics",
        "-XX:+UseFMA",
        "-XX:+UseLoopPredicate",
        "-XX:+RangeCheckElimination",
        "-XX:+OptimizeStringConcat",
        "-XX:+UseCompressedOops",
        "-XX:+UseThreadPriorities",
        "-XX:+OmitStackTraceInFastThrow",
        "-XX:+RewriteBytecodes",
        "-XX:+RewriteFrequentPairs",
        "-XX:+UseFPUForSpilling",
        "-XX:+UseFastStosb",
        "-XX:+UseNewLongLShift",
        "-XX:+UseVectorCmov",
        "-XX:+UseXMMForArrayCopy",
        "-XX:+UseXmmI2D",
        "-XX:+UseXmmI2F",
        "-XX:+UseXmmLoadAndClearUpper",
        "-XX:+UseXmmRegToRegMoveAll",
        "-XX:+EliminateLocks",
        "-XX:+DoEscapeAnalysis",
        "-XX:+AlignVector",
        "-XX:+OptimizeFill",
        "-XX:+EnableVectorSupport",
        "-XX:+UseCharacterCompareIntrinsics",
        "-XX:+UseCopySignIntrinsic",
        "-XX:+UseVectorStubs",
        "-XX:UseAVX=2",
        "-XX:UseSSE=4",
        "-XX:+UseFastJNIAccessors",
        "-XX:+UseInlineCaches",
        "-XX:+SegmentedCodeCache",
    };
}
