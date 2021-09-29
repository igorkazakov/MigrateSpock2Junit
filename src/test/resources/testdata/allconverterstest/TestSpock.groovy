package testdata.allconverterstest

import com.google.gson.JsonPrimitive
import kotlin.Unit
import kotlin.jvm.functions.Function1
import org.junit.ClassRule
import ru.alfabank.stubs.AboutMediator
import ru.alfabank.stubs.AboutMediatorImpl
import ru.alfabank.stubs.ElectricSpecification
import ru.alfabank.stubs.PresenterRoboRule
import ru.alfabank.stubs.ShareUtils
import spock.lang.Shared
import spock.lang.Unroll

class Mediator extends ElectricSpecification {

    @Shared
    @ClassRule
    PresenterRoboRule presenterRule

    def eer = new PresenterRoboRule()

    ShareUtils shareUtils = Mock()

    def defaultHttpErrorChecker = Mock(HttpErrorChecker)

    AboutMediator mediator = new AboutMediatorImpl()

    ShareUtils222 shareUtils222 = Mock {
        buildEmailIntent(*_) >> new Intent(Intent.ACTION_SENDTO)
    }

    ContactsProvider contactsProvider = Mock {
        getPhoneContactInfo(*_) >> [
                new BankPhoneContactInfo("7 900 009 35 12", "Москва"),
                new BankPhoneContactInfo("8 800 555 35 35", "Россия")
        ]
    }

    ResourcesWrapper resourcesWrapperMock = Mock {
        getString(R.string.card_common_action_section_info, _, _) >> "Информация"
    }

    def createOperationTime() {
        return CalendarTestUtils.getCalendar(2003, Calendar.FEBRUARY, 25, 10, 15, 0)
    }

    def sunction() {
        return ExceptionTest().getException(2003)
    }

    def sunctionInt() {
        return 34
    }

    def 'should open AboutActivity'() {
        given:
        def versionName = "10.3.2.1"
        storage.preferences122 >> 33
        def versionCode = 1030204
        interactor.executeTransferForOperationConfirmation(*_) >> { _, SingleObserver<FinalPaymentResponse> observer ->
            Single.just(expectedFinalPaymentResponse).subscribe(observer)
        }
        featureToggle.isEnabled(Feature.TEMPLATES_V2_ADD_REGULAR) >> isCheckedFeature
        interactor.loadPreferences(_) >> { SimpleSingleObserver observer, Error type ->
            observer.onSubscribe(Mock(Disposable))
            observer.onSuccess(expectedResponse)
        }
        1 * prese34nter.onShare(77)
        2 * prese678nter.onShare1() >> 56
        Function1<OperationConfirmationResultModel, Unit> resultConsumer = null
        def expectedAction = null
        router.registerOperationConfirmationResult(_, 23) >> { Function1<OperationConfirmationResultModel, Unit> actualConsumer ->
            resultConsumer = actualConsumer
        }
        presenterRule.onStart(Mock(Context))
        when:
        mediator.startAboutActivity(presenterRule.activity)
        then:
        presenterRule.nextActivity(AboutActivity)
        presenterRoboRule.nextWithIntentAction(Intent.A)
        presenterRoboRule.nextActivityWithIntentAction(Intent.ACTION_SENDTO)
        actual == expected
        0 * presenter.onClicked()
        1 * presenter.onShareClicked()
        2 * presenter.onClickedMultiple()
        1 * resourcesWrapper.getString(R.string.base_device_list_last_entered_at, _, _) >> { _, args ->
            assert args[0] == expectedTimeDeltaToFormat
            "Последний вход 5 минут назад"
        }
        1 * resourcesWrapper12.getString4(R.string.base_device, _, 23) >> { _, args ->
            assert args[0] == expectedTimeDeltaToFormat
            minute = 56
        }
        1 * resourcesWrapper99.getString66(_, _, 23) >> { _, _, args ->
            assert args == expectedTimeDeltaToFormat
            assert args == expected34444
            minute == 56
        }
        1 * view.show222Options1Dialog(*_) >> null
        1 * featureCacheCleaner.loadData() >> null
        1 * featureCacheCleaner.loadData(_, cacheKey, memoryTime) >> null
        1 * feature.loadData(cacheKey) >> null
        1 * shareUtils.openInternetAddress(_) >> { String actual ->
            assert actual == expected
        }
        1 * view.showNumberOptionsDialog(*_) >> { args ->
            assert args[0] == expectedTitles
            assert args[1] == expected333
        }
        1 * presenter.populate(_, _) >> { String actualTitle, ArrayList<WidgetDto> actualWidgets ->
            assert actualTitle == widgetState.widgetData.title
            assert actualWidgets == widgets
        }
    }

    @Unroll
    def 'should open 1234'(Number url23) {
        expect:
        presenterRule.nextActivity(AboutActivity)
    }

    def 'should open AboutActivity2'(String url, String screenTitle, Boolean paySupported) {
        presenterRule.nextActivity(AboutActivity)
    }

    def setup() {
        feature1.load() >> null
        presenterRule.nextActivity(AboutActivity)
    }

    def cleanupSpec() {
        RxJavaPlugins.reset()
    }

    @Unroll
    def 'should deserialize string'() {
        when:
        def actual = serializer.deserialize(new JsonPrimitive(initialString), String, context)
        then:
        actual == expected
        where:
        initialString    | expected     | expected777
        "initialString1" | "expected1"  | "expected7771"
        "initialString2" | new Chelik() | "expected7772"
        "initialString3" | "expected3"  | "expected7773"
        "initialString4" | "expected4"  | new Parent()
    }

    @Unroll
    def 'should apply date filter when calendar screen closed and #name'() {
        given:
        def filter = OperationsHistoryTestDataKt.getTestOperationsHistoryFilterBeforePickCalendarRange()
        and:
        presenter.onViewCreated()
        mediator.startAboutActivity(presenterRule.activity) >> MediatorResult()
        when:
        activityResultCallback.invoke(resultModel)
        then:
        1 * quickChipsMapper.mapToModel(_) >> { OperationsHistoryFilter actualFilter ->
            assert actualFilter == expectedFilter
            OperationsHistoryTestDataKt.getQuickChipsModel()
        }
        where:
        [resultModel, expectedFilter, name] << [
                [
                        OperationsHistoryTestDataKt.getCalendarScreenSuccessResultModel(),
                        OperationsHistoryTestDataKt.getTestOperationsHistoryFilterAfterPickCalendarRange(),
                        "new range selected"
                ],
                [
                        OperationsHistoryTestDataKt.getCalendarScreenResetResultModel(),
                        OperationsHistoryTestDataKt.getTestOperationsHistoryFilterAfterResetCalendarRange(),
                        "reset selected range"
                ]
        ]
    }
}