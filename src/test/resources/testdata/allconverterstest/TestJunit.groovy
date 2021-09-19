package testdata.allconverterstest

import kotlin.Unit
import kotlin.jvm.JvmField
import kotlin.jvm.functions.Function1
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.alfabank.stubs.AboutMediator
import ru.alfabank.stubs.AboutMediatorImpl
import ru.alfabank.stubs.AlfaRobolectricRunner
import ru.alfabank.stubs.ElectricSpecification
import ru.alfabank.stubs.PresenterRoboRule
import ru.alfabank.stubs.ShareUtils
import spock.lang.Shared
import spock.lang.Unroll

@RunWith(AlfaRobolectricRunner::class)
class Mediator {

    @JvmField
    @Rule
    val presenterRule = PresenterRoboRule()

    val eer = PresenterRoboRule()

    val shareUtils = mock<ShareUtils>()

    val defaultHttpErrorChecker = mock<HttpErrorChecker>()

    val mediator = AboutMediatorImpl()

    val shareUtils222 = mock<ShareUtils222>() {
        on { buildEmailIntent(any()) } doReturn Intent(Intent.ACTION_SENDTO)
    }

    val contactsProvider = mock<ContactsProvider>() {
        on { getPhoneContactInfo(any()) } doReturn listOf(BankPhoneContactInfo("7 900 009 35 12", "Москва"), BankPhoneContactInfo("8 800 555 35 35", "Россия"))
    }

    val resourcesWrapperMock = mock<ResourcesWrapper>() {
        on { getString(eq(R.string.card_common_action_section_info), any(), any()) } doReturn "Информация"
    }

    @Test
    fun should_open_AboutActivity() {
        // given
        val versionName = "10.3.2.1"
        whenever(feature.loadData(eq(cacheKey))).doReturn(null)
        whenever(featureCacheCleaner.loadData(any(), eq(cacheKey), eq(memoryTime))).doReturn(null)
        whenever(featureCacheCleaner.loadData()).doReturn(null)
        whenever(view.show222Options1Dialog(any())).doReturn(null)
        whenever(resourcesWrapper.getString(eq(R.string.base_device_list_last_entered_at), any(), any())).doReturn("Последний вход 5 минут назад")
        val versionCode = 1030204
        whenever(interactor.executeTransferForOperationConfirmation(any())) doAnswer {
            val observer = it.arguments[1] as SingleObserver<FinalPaymentResponse>
            Single.just(expectedFinalPaymentResponse).subscribe(observer)
        }
        whenever(featureToggle.isEnabled(eq(Feature.TEMPLATES_V2_ADD_REGULAR))).doReturn(isCheckedFeature)
        whenever(interactor.loadPreferences(any())) doAnswer {
            val observer = it.arguments[0] as SimpleSingleObserver
            val type = it.arguments[1] as Error
            observer.onSubscribe(mock<Disposable>())
            observer.onSuccess(expectedResponse)
        }
        val resultConsumer: Function1<OperationConfirmationResultModel, Unit>
        val expectedAction = null
        whenever(router.registerOperationConfirmationResult(any(), eq(23))) doAnswer {
            val actualConsumer = it.arguments[0] as kotlin.jvm.functions.Function1<OperationConfirmationResultModel, kotlin.Unit>
            resultConsumer = actualConsumer
        }
        presenterRule.onStart(mock<Context>())
        // when
        mediator.startAboutActivity(presenterRule.activity)
        // then
        assertTrue(presenterRule.nextActivity(AboutActivity))
        assertTrue(presenterRoboRule.nextWithIntentAction(Intent.A))
        assertTrue(presenterRoboRule.nextActivityWithIntentAction(Intent.ACTION_SENDTO))
        assertEquals(actual, expected)
        verify(presenter, never()).onClicked()
        verify(presenter).onShareClicked()
        verify(presenter, times(2)).onClickedMultiple()
        verify(resourcesWrapper).getString(eq(R.string.base_device_list_last_entered_at), eq(expectedTimeDeltaToFormat), any())
        verify(resourcesWrapper12).getString4(eq(R.string.base_device), eq(expectedTimeDeltaToFormat), eq(23))
        verify(resourcesWrapper99).getString66(eq(expectedTimeDeltaToFormat), eq(expected34444), eq(23))
        verify(view).show222Options1Dialog(any())
        verify(featureCacheCleaner).loadData()
        verify(featureCacheCleaner).loadData(any(), eq(cacheKey), eq(memoryTime))
        verify(feature).loadData(eq(cacheKey))
        verify(shareUtils).openInternetAddress(eq(expected))
        verify(view).showNumberOptionsDialog(eq(expectedTitles), eq(expected333))
        verify(presenter).populate(eq(widgetState.widgetData.title), eq(widgets))
    }

    @Test
    fun should_open_1234(url23: Number) {
        // expect
        assertTrue(presenterRule.nextActivity(AboutActivity))
    }

    fun should_open_AboutActivity2(url: String, screenTitle: String, paySupported: Boolean) {
        presenterRule.nextActivity(AboutActivity)
    }

    @Before
    fun setup() {
        whenever(feature1.load()).doReturn(null)
        presenterRule.nextActivity(AboutActivity)
    }
}