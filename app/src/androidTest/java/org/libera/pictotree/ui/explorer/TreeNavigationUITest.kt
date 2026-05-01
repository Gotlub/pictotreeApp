package org.libera.pictotree.ui.explorer

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.libera.pictotree.MainActivity
import org.libera.pictotree.R

@RunWith(AndroidJUnit4::class)
class TreeNavigationUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testExplorerUIElementsVisible() {
        // This test assumes the app starts and reaches TreeExplorerFragment
        // In a real scenario, you'd navigate there first or use a FragmentScenario
        
        // Check if essential buttons are displayed
        onView(withId(R.id.fab_search)).check(matches(isDisplayed()))
        onView(withId(R.id.fab_eye)).check(matches(isDisplayed()))
        onView(withId(R.id.fab_speak)).check(matches(isDisplayed()))
    }
}
