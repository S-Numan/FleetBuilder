package starficz

class Flag() {
    var isChecked: Boolean = true
        internal set

    var isUnchecked: Boolean
        get() = !isChecked
        internal set(unchecked) {
            isChecked = !unchecked
        }
}