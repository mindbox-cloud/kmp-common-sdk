package cloud.mindbox.mobile_sdk.annotations

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
@RequiresOptIn(
    message = "Internal API. Use only inside Mindbox SDK.",
    level = RequiresOptIn.Level.WARNING
)
public annotation class InternalMindboxApi
