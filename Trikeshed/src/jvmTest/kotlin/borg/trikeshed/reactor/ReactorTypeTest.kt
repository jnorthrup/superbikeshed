package borg.trikeshed.reactor

import kotlin.test.Test
import kotlin.test.assertIs

class ReactorTypeTest {
    @Test
    fun `should resolve type conflicts between expect and actual declarations`() {
        // Verify ServerChannel type consistency
        assertIs<SelectableChannel>(ServerChannel())
        
        // Verify ClientChannel type consistency  
        assertIs<SelectableChannel>(ClientChannel())
        assertIs<WritableChannel>(ClientChannel())
    }
}