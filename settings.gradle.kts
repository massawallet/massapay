rootProject.name = "MassaPay"

include(":app")
include(":core")
// Removed missing modules ':data' and ':domain' which are not present in the workspace.
// If you add them later, re-add these lines:
// include(":data")
// include(":domain")
include(":ui")
include(":security")
include(":network")
include(":price")