package opensource.cached_dupe_scanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppWorkScopes {
    val scanScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
