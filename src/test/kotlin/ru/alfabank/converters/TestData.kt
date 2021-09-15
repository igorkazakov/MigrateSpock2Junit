package ru.alfabank.converters

val spockTest = """
    import hkhc.electricspock.ElectricSpecification
    import org.junit.ClassRule
    import ru.alfabank.mobile.android.about.mediator.AboutMediatorImpl
    import ru.alfabank.mobile.android.about.presentation.activity.AboutActivity
    import ru.alfabank.mobile.android.aboutapi.mediator.AboutMediator
    import ru.alfabank.mobile.android.test.rules.PresenterRoboRule
    import spock.lang.Shared
    import spock.lang.Unroll

    class SpockTest extends ElectricSpecification {

        @Shared
        @ClassRule
        PresenterRoboRule presenterRule

        def eer = new PresenterRoboRule()

        ShareUtils shareUtils = Mock()

        AboutMediator mediator = new AboutMediatorImpl()

        def 'should open AboutActivity'() {
            when:
            mediator.startAboutActivity(presenterRule.activity)
            then:
            presenterRule.nextActivity(AboutActivity)
        }

        @Unroll
        def 'should open 1234'() {
            expect:
            presenterRule.nextActivity(AboutActivity)
        }

        def 'should open AboutActivity2'() {
            presenterRule.nextActivity(AboutActivity)
        }

        def setup() {
            presenterRule.nextActivity(AboutActivity)
        }
    }
""".trimIndent()