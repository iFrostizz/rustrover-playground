import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

class ScratchExecutionBundle : DynamicBundle(PATH_TO_BUNDLE) {

    companion object {
        const val PATH_TO_BUNDLE: String = "messages.ScratchConfigBundle"
        private val ourInstance: AbstractBundle = ScratchExecutionBundle()

        @Nls
        fun message(
            @PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String,
            vararg params: Any
        ): @Nls String {
            return ourInstance.getMessage(key, *params)
        }

        fun messagePointer(
            @PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String,
            vararg params: Any
        ): Supplier<String> {
            return ourInstance.getLazyMessage(key, *params)
        }
    }
}