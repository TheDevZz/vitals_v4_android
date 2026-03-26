package com.vitals.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vitals.example.databinding.ActivityMainBinding
import com.vitals.sdk.api.MeasureResult
import com.vitals.sdk.api.Vitals
import com.vitals.sdk.api.VitalsSdkConfig
import com.vitals.sdk.api.VitalsSdkInitCallback
import com.vitals.sdk.api.VitalsSdkInitOption
import com.vitals.sdk.parcel.Credential
import com.vitals.sdk.parcel.ParcelableVitalsSampledData
import com.vitals.sdk.parcel.SignalData
import com.vitals.sdk.solutions.live.NativeAnalyzer
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

class DevActivity : AppCompatActivity() {
    lateinit var viewBinding: ActivityMainBinding
    var hasInitialized = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.btnFace.setOnClickListener {
            if (hasInitialized) {
                startActivity(Intent(this, FaceActivity::class.java))
            } else {
                Toast.makeText(this, "sdk未初始化", Toast.LENGTH_SHORT).show()
            }
        }

        viewBinding.btnTest.setOnClickListener {
//            reTest()

//            testSignalJson()
            compareSignalJsonBatch()
        }

        setupSdk()
    }

    private fun setupSdk() {
        viewBinding.echo.text = "SDK初始化中"

        val option = VitalsSdkInitOption()
        option.appId = "mfz8msj23z91w1z8"
        option.appSecret = "oegyckv591wf0zf3dd9lnruqt53y0lqo"
        option.outUserId = "1"
        val cfg = VitalsSdkConfig()
        cfg.enableLog = true
        cfg.enableDebug = true
        Vitals.getSdkInstance().initialize(this, option, cfg, object : VitalsSdkInitCallback {
            override fun onSuccess() {
                viewBinding.echo.text = "SDK初始化成功"
                hasInitialized = true
            }

            override fun onFailure(errorCode: Int, errMsg: String, t: Throwable?) {
                viewBinding.echo.text = "SDK初始化失败 $errMsg $t"
            }
        })
    }

    private fun reTest() {
        // cacheDir.listFiles()?.first()?.let {
        //     DataBridge.readParcelableFromFile<ParcelableVitalsSampledData>(it, classLoader)?.let { data ->
        //         viewBinding.echo.text = "读取到文件数据"
        //
        // }
        startActivity(Intent(this, ResultActivity::class.java))
    }

    private fun buildEmptySampledData() {
        ParcelableVitalsSampledData(
            Credential("", System.currentTimeMillis(), ""),
            SignalData(0L, 0L, 0.0, DoubleArray(0), IntArray(0)),
            emptyList(),
            emptyList(),
        )
    }

    private fun testSignalJson() {
        val signalJsons: List<String> = arrayListOf(
//            "{\"base64Pixels\":\"Qyp1x0MUslBDAfYrQyjqoUMTojFDATz5Qyp9\\/UMUZqBDAkYtQytQQ0MVEplDAuQUQyswzEMUgnZDAdThQyo3MkMTr0FDAPrmQyojnUMTtqFDAU4KQyq\\/4kMT0s9DAWHEQykDwUMR5L1C\\/pi3QyZ7T0MPchhC+pzFQybOW0MQUi5C+8UQQyf4OkMRJDhC\\/NJgQyabHEMP9+JC+uqpQybxeUMQI\\/tC+9tzQydw9EMQxNlC\\/PiNQydyp0MQzBFC\\/Nj0QybyJEMP3DNC+5DtQya6nEMPfmRC+lp7QyYlS0MPgsJC+ZSAQyYfDkMPG55C+MsFQyYMpkMOtA1C+AfRQyerfEMPxwFC+WmCQyYCNUMOrZpC+XavQyRr4kMNUspC9yJ7QyQFiEMNkVpC9qzOQyScz0MOCDxC95BXQyJZ70MMZG9C86lcQyGG4EMLiStC8pbxQySKL0MOA+9C9x9BQyVPC0MPYntC+UtAQySaoEMN95RC91inQyP44UMNJqhC9YrVQyUvw0MPKs9C+OCIQyPNPUMOG3dC948jQyYlf0MPnB1C+Z5kQyjTpkMR56dC\\/haJQyiBIkMSdPNDADgFQyeI\\/UMSC2tDAAvtQyrRREMUPoJDAaNKQy1shkMWVGBDAzDMQyu\\/y0MVbVVDAll0QyqReEMUFrVDAPiYQyov7kMTzfdDAEYVQyoq3EMTj2JDAKTxQyjQ0kMSRfFDAD0iQykqmEMSsB5DAI36QyjRkEMSxnRDAQ2YQyiHGkMSkDxDAMuDQyhC70MR4\\/hDAFhzQyibU0MSSahDAHFhQyY7HEMP02NC\\/LaMQyPm3kMNVqFC+DQ7QyYaUkMPPiNC+MDaQyVdFkMOcxhC9z+0QyTvVEMN54dC99hXQyTq8kMNqNNC94rUQyTt8kMN4Y1C9gdTQyVtYUMN5\\/lC9sJQQyTPBUMNWP5C9lkpQyTrWEMNiqpC9vLxQyZq6EMPFFJC+wi4Qyd6H0MP3fdC\\/HaBQykhwUMSVy1DAKQDQypWW0MT3IFDAi0zQyoFhkMTl1dDAozrQyqYXEMUDGVDAt27QypD+kMUhwhDAyTKQyo4bEMUq\\/lDA3w5Qypk\\/0MUoQ9DAyS+QyohZUMUkVZDAu4qQyqAYUMUes9DA1SsQyqRRUMUmT5DA6YmQyoGKUMU1AJDAwe4QyilC0MTs8NDAYBjQymW4EMTB45DAYEWQyk7y0MSfy1DAMsaQymBEkMTVgpDAM8XQyiZrEMSgWtC\\/+HXQyjbrEMSF2tC\\/+i3QylB1kMShkNDAFhNQyi1H0MSchhDAGBFQyftaEMSCVdC\\/\\/UiQyieX0MSb6NDAHwtQyjjQEMSuKtDAIpZQyiFJUMSWDNDAG7rQyekIUMR0mFDAE1FQygi8kMSD3pDAHimQykf6EMSuIFDAOB+Qygpm0MSEndDAH8fQybnGEMQ0DZC\\/3xSQyhaGEMR\\/lhDAJ3mQyk5pEMS7KtDATD5QyjPf0MScjtDAUTlQyjJZkMShqhDAUGlQyl9zkMTXE9DAYGiQymVd0MTPg9DANORQyiEfUMRpmRC\\/+c1QyiBFEMRkPFC\\/4uTQyifQUMRKu5C\\/ULuQyff+kMQupxC\\/FdJQyaJGkMPlbdC++BwQycvdUMP43NC\\/JJDQyY1ukMO+9tC+h6FQyS1s0MNhDlC98ItQyVhX0MOIgRC+WQQQyWbPkMOVkRC+dMaQyTQxkMN6ahC+MsUQyQ9B0MNYHNC99jNQyRvrUMOHvhC99VCQyRY7kMOQYdC982SQyQl50MN7MZC9vLAQyM84kMNEURC9WGtQyLF\\/0MMjuBC9HItQyNK60MMuDpC87ReQyOsT0MMlrlC88BnQyL5KkMMI6tC86Y\\/QyS03EMN6DZC9h\\/XQyUN20MOfehC93BuQyTXc0MOcjhC+O+6QyZWCEMQSKRC\\/RqnQyhxVkMR\\/FxC\\/t9YQyfuG0MRhVpC\\/nQZQyhzCUMScsxDAC+OQylBoEMTRzhDATevQyloqUMTAbNDALAZQykJR0MSzw9DAIHIQyls5kMTZWRDAUIyQymtEUMT8pFDAfZYQyr6cEMVCNNDAt9mQyvUg0MWFglDA+rhQy4SH0MX22lDBULGQy1Np0MXacRDBQnBQyvMjUMWcpdDBKngQyuTSkMWbxxDBHAyQyqu60MUdx5DAyLFQygAqEMRfRxDABclQykNwEMSL4tC\\/wofQycV90MQU3dC+7AdQyZtmkMPNUhC+ppAQyZRAEMOx7FC+Q38QyfcqkMQeVhC+pqeQyhxiUMQ+HNC+67lQyjcMEMRvNZC\\/SWAQyj1x0MR3lZC\\/YmjQyfPXUMRIBRC\\/WcpQydkQ0MQmZtC\\/Pw8QycpxEMQZlxC\\/Q3sQydzdUMQjxtC\\/PVTQyeat0MRGp9C\\/0PSQydROUMQlE9C\\/pHYQyg98kMRo0FDAJK6QyjW6UMSs9ZDAWZNQyjnY0MSnuNDAZUXQynVz0MTStlDAoLiQyjWI0MSnMVDAXWWQydBlEMRfedDAC4rQycy80MRNDhC\\/91CQycx5kMRGRpDAACgQybw5kMQzypC\\/SegQyZ45UMQIqpC+6xhQyb47EMQXMRC\\/P1cQyY2QkMPmgFC\\/A\\/NQyYZv0MPPbdC+6jxQyYlVEMPGcVC+5EIQyYSLEMPLllC+rLvQyW0wUMOxeBC+bpYQyc750MQCahC+224QydPSUMQsEFC\\/LG+QyakSUMQdLBC\\/I\\/yQybYE0MQdCJC\\/VMrQyaz9UMQef5C\\/QTuQyaoDEMQarhC\\/TjwQydpOkMQrf1C\\/gAOQyZ\\/kkMQBcBC\\/PGqQyar7UMQNIdC\\/Mn2QydGKEMQWrdC\\/FHgQya93kMP2KhC+2tnQyajW0MPyyJC+yKmQyaotUMPepJC+drPQyYmqEMOuINC+MpAQyVGqkMOdCVC96y9QyVZ4kMOfNJC9zDPQyTP4UMN+5RC9i4XQyQPaEMNYUVC9JDuQyTG50MNlpBC9Vq3QyPMG0MMsjpC8xHoQyOWbEMMXTtC9Bc2QyQOKkMNJ5dC9hNIQyT7cUMOc+FC+UBeQyZ1oUMPu4ZC+848Qyd++0MQo3RC\\/sJlQyf2E0MRRyZDADPhQyhUrkMRkDpC\\/5bGQyjNs0MSHzVDAF7pQyffEkMSAyVDABCgQyf2hEMSGzxDAEEKQykWVUMTZPpDAUWmQynAh0MT1VpDAcJgQyoz50MUkTVDAe+PQyptq0MU1JlDAls2Qyqix0MVDHdDApGFQyvKZkMWEZRDA23QQyrAm0MU47tDAm0iQyl32EMTlDdDAPzPQytuBEMUva1DAmcEQytAiUMVGJdDApRzQyrIo0MVIR9DAthsQyqIcUMUnEdDAkmOQysTE0MUzWRDAjvxQytz\\/EMU\\/HxDApyxQyq8UkMUf6FDAjE7QyoHKUMTumhDAUX6QymdqUMTx3hDAYjgQykp4UMTK0VDALpbQyllhEMS5LVDADqjQymeckMTZQlDAKavQyqIWUMUTZ5DAdciQysnaUMU+vpDAr8HQyt1nEMVIRtDAr7ZQyt54EMVHb5DAp9XQyq71EMUibxDAhrRQyjteEMSyZFDALlzQyguMUMR3UhC\\/512QydeakMQr5hC\\/NizQycDHUMQU+dC\\/BG8QyY0W0MPRslC+l9EQyZJi0MPV6dC+a3KQyaut0MPmFFC+m8qQybxo0MP8FdC+u7yQyapTUMPvuRC+t0hQydV2kMQcxlC+\\/SCQyePlkMQuDFC\\/FoUQycsKkMQMIVC++0mQyX9NEMPUchC+oEGQyZ1ekMPH8NC+v2cQyaumUMPYLBC+xRzQyaaOEMPtdpC+vbMQyZjLEMPsJBC+t9FQycvIUMQl1JC\\/YGeQygSwkMRbBBC\\/4y3QyfzzkMRNfFC\\/74WQyhr0UMR2AdDAE7vQyh3MUMRo+xDABAbQygnD0MRNDlC\\/5kTQyhCuEMRawZC\\/WCmQycwPkMQiL1C+92OQydk00MQVApC+6fNQyc53kMQNdZC+z4bQycFxEMQD5hC+sEKQydpLkMQdllC+7C8QydfdkMQg0lC+zDlQybbG0MQDjBC+oi+QyaIuEMP\\/C5C\\/EBpQyXRaEMPhZJC+5NtQyXTrkMPMQhC+9CxQyXN20MPYMJC+5ESQyZ0dEMP1+5C+yZEQySqOEMOjudC+WTxQyZarUMPqmNC+5dfQyeUX0MQf5NC\\/P9yQyaUFkMQRm5C+\\/TxQyaN4UMQV0VC+7jQQyacK0MP5LtC+8kxQyZOCEMPdJ9C+W5lQyVwFUMO25ZC+Nk9QyT9bkMOdwdC+GZwQyUX3kMOybVC+RAPQyVD5EMO\\/qpC+RjjQyYO9kMPPUNC+cSIQyXtNUMPFrdC+e8JQyXtVkMPjftC+lFKQyZhe0MQLoRC+1BlQybf2UMP\\/0VC+zCnQyfNIEMQpZVC\\/LLsQyff3UMRHYxC\\/hyPQyegnEMRL8FC\\/c8VQybV\\/kMQp2ZC\\/nwDQyadfEMQbo5C\\/eS+QyZAY0MP\\/\\/VC\\/Za3QyYlYkMP8d1C\\/Y+lQycszkMQQEFC\\/gpDQybPXUMQH8FC\\/dyZQygA5UMQ1hpC\\/YVsQyhxtEMRM2NC\\/f\\/5Qye8hkMQ3RtC\\/W0eQyh6jkMRd9xC\\/mIeQyipf0MRnolC\\/ydRQyg4eEMRWvZC\\/snJQyeNAEMRv2pC\\/4r1QyepNkMR3rtDABNQQycQwUMRpXxC\\/9vBQyb1W0MRwwJDAAgdQyiVBkMSW41DAJuyQymkiEMTNyRDARB0QyiE70MSQB5DAP8PQydo2UMRUTpDACkyQyiatUMSKXtC\\/8aqQyidDUMRp3VC\\/4zJQyenREMRNO1C\\/u4YQyct20MRK+lC\\/5I3QygZl0MR3n5DACjMQylGD0MSv65DAPIQQyhQF0MS0p1DAJfTQyeM6EMSXhBDAE5ZQyiSTEMTVINDAOPcQyi3GEMTXotDAQ\\/1Qyjg4kMTaCpDARC5QyiDIUMTTlxDAPIFQyiYJ0MTZtZDAP5NQykaBUMTw8JDAX0rQyjbWEMTBgRDAODpQyiGV0MSiM5DAIprQynHWEMTTd5DAMf+Qyl2HEMTCoJDAKk4QylOUkMTF9BDAJ6RQym4e0MTObJDAJSuQyoGKUMTWsVDAPFVQyke3UMSviBDADf7QyiJyUMSQltC\\/17hQyjXtkMSS+5C\\/yO2QykFhEMSvRdC\\/6kRQygoeEMSFyFC\\/rtkQyjuX0MSqWxDAFX5QymevUMTWuVDAOZyQyj7UkMSnytDAEJKQyi5nkMSg6pDAAxLQylNo0MSzvJDAD3VQykr+UMSvqNDAA+3Qyj+zEMSkRdDAGHXQyfZM0MRgMtC\\/pR8Qyg9CEMRyQRC\\/zBtQyiVcEMR83BDAAqrQygb5kMSPIhDAEo8QyiQtUMSrrRDANMsQyi+mEMTIedDAV7XQykIV0MTftpDAaQLQylymkMTtmVDAe+wQyk4MUMTlBZDAebkQymIHUMT0WhDAg8qQymQNUMTu3JDAfd\\/QyoHJUMT3dlDAft7QyoN7UMTtY9DAcz2QypiLkMTu+1DAaJRQypocUMT+O1DAdobQypKVUMUR\\/VDAndiQyoxrkMUQENDAoQuQypDtUMUfINDAnPPQypuYEMUbtNDAqODQyqEk0MUZmpDAoUnQym7DUMT2HNDActeQyljXkMTg8hDAbWhQynbaUMTb8lDAdYUQyl67kMTgvBDAUuTQynCBkMUCG5DAb4nQynMgUMT\\/bVDAkrcQynXHkMUGmBDAjqwQyoEO0MUaaZDAmDHQymxo0MUSPZDAlG6Qynd\\/kMUAdxDAnX1QymYvUMT1gBDAf1IQyoOaEMUFcNDAiuJQyp4kUMUZ2ZDAmbLQyqD4kMUEVxDAfl2QynoNUMTbBZDAWK4Qyk0IkMTAtZDATMWQyiFQkMSlUZDAPobQyjIt0MSTwxDAEx8QyjcoEMSU\\/hDACDmQyktB0MSsqhDAL5SQymyRUMS4FhDAMdQQykqrEMS12FDAKCHQyjMt0MSdf1DAFxNQykhY0MS3FVDAJvuQyleeUMTBv5DAKwPQynA6EMTPtJDAQzKQynG3kMTbgFDAP0\\/QyogykMT6QhDAToOQyi8H0MSx+1DALwdQyn0X0MTzHRDAdE8Qyrd4EMU4bdDAqIDQynClUMTyGJDAbvjQynOX0MUCTJDAg9QQynq30MUNdVDAnGjQynXdUMUPH9DApP\\/QynvBkMUMINDAk+oQylOtEMTzRBDAbnZQyjaR0MTQCRDAXmqQyiq3UMS4oBDASdCQyj4AUMS9s5DAXtTQyjYbEMTDLBDAU1cQykQ2kMTJN9DARzYQyj9XEMS\\/AFDARA5QyiDq0MSta9DAOReQyhqzUMSnONDAMCZQyfbHkMSg7NDAH8qQyf4L0MSq+hDAJUjQyi3r0MTKzBDAZKzQygHP0MS1vBDAYwAQym2\\/EMUHGRDApmbQyqOPUMVEuRDA4CMQyoBOkMU4E9DA43iQyqAl0MVMsZDA+pnQypznkMU2yNDA27qQymmlkMUlFdDAwu7Qypa\\/0MVL51DA8jLQyqo4UMVqH1DBBVAQyrWNUMVvr9DBDcWQyrwvUMV3YJDBGMTQytCMUMWAXhDBGnBQyqsc0MVXB9DA9DxQyneDEMUYHZDApe9Qyhqh0MStvZDANKYQyi5q0MSkyBDALATQyf+DEMSNWhDAGYoQyfZxkMRtVBC\\/6GcQyc5CkMREFZC\\/jUPQya7CEMQYslC\\/NnUQyYUXUMPojFC+8lsQyXcrUMPQjZC+knbQyWzCUMO9lZC+hf3QyU8f0MPBxFC+iHcQyT\\/MEMOtpFC+cQLQySzdkMOZ3NC+eNsQyS3aUMOXIhC+ii0QyUNRkMOl51C+covQyYEyEMPXhZC+r4HQyT+60MOtLBC+q1VQyRu4kMOOkZC+iCuQyUN60MOm89C+eOkQyXCbUMPN8pC+vqFQyWIg0MPC9BC+nElQyTibkMOmuNC+eohQyYEi0MPZk9C+2kiQycdo0MQoe1C\\/YYEQycTqkMRNIJC\\/uZ5QydRhEMRm7lDAExgQyhxnkMSooVDAQ6hQyj6qUMTMEJDAXxhQyi9F0MTdWJDAYjeQyi2uUMTUlhDAdlkQylPyEMTvalDAgijQykz40MThaFDAgGYQyjLfEMTgIJDAh1aQylxBUMT6WxDAlsUQyo1b0MUl49DAtIPQyk0hUMUA01DAq+hQypOYUMVKAdDA5bLQyuXJ0MWWb1DBL2ZQyqdnUMVt8FDBB7kQyokrEMVgehDA\\/RiQysvDUMV2NVDBHDuQytM8UMV\\/8pDBJNaQyrbm0MV4itDBHVhQyrxsUMWHHRDBM3GQyusGEMWZylDBQhCQyufHkMWe9hDBQHaQywcVUMWPTFDBHHgQyxri0MWlldDBJRvQysZ2UMVlylDBAw9QyngikMUgDVDAuL7Qyo\\/I0MUwJpDApgoQyo+1EMUZZZDAnitQyoGmEMT94pDAlKmQym9MEMT9L9DAi7UQynehkMUYlFDApMaQyk4KEMT7KtDAjaPQykVKEMTfY9DAS7XQylYuUMTefZDASE0QymQS0MT\\/NtDAeOaQyiW1UMTLVdDAU5CQynnPEMUE29DAaIiQyp+EkMUzVVDAl8RQyo36kMUOoFDAjXcQyp110MUuh1DAszZQyptBEMU8YZDAthiQypxGEMVD+9DAwjlQysNC0MVh2FDA8wAQyqNvkMVIKlDA5kKQyq8o0MVa0FDA6EMQyrXiEMVaK9DA5TcQyqZoUMVLNBDA5DyQypst0MVQ15DA7szQypwkUMVc2hDA9GHQypitUMVhA9DA+mpQyqDe0MVTlVDA8LJQyrWP0MVdftDA+GOQyrnWUMVig5DA8r9QyqOS0MVZeBDA9EgQyohpEMU70pDA4LrQyoqNkMU4clDA4t1Qyml9EMUIzlDAuLgQyiZ1kMTNRNDAdYRQymXUUMThCtDAdFSQyldVkMTUXJDAbU0QykNRkMTE31DAZjCQyjszEMSxAtDARVKQyiZ+kMS+6VDARhJQykCgEMTPAlDAY\\/rQykMNkMS0l1DAR2UQyiE9UMSVYVDAEbuQyioVUMSlZpDAN3oQyirNkMSssBDAQohQyjRp0MS5CZDAVIeQykCsEMS4b5DARg2Qyg90kMSLpRDAL8xQyhalkMSG0hDAJOTQyfXmkMRooFC\\/vV3QyfBAUMRWXRC\\/lreQygwKkMR601C\\/8h5Qyek6UMRPiBC\\/tmmQyeBWkMQ+GBC\\/gA4QycW6kMQyhNC\\/YC2QybRy0MQtidC\\/j9YQyarSUMQfAJC\\/iL3Qyclo0MRMVtC\\/t7yQye++UMR07tDAD8tQygNKUMSIoRDAJr7QyfW1UMRzxxDAGjJQyiXCEMS4KdDAbjQQyjhdkMTi8FDAm07QykyQkMUDTBDAxvMQynEi0MUxBtDBArNQyl7X0MVCqNDBF9aQymjc0MVNVNDBJl7QyrNkkMWG65DBZpXQytOikMWkhdDBgosQytleUMWyWNDBjcVQytRv0MWlbRDBfH\\/QytK8kMWiylDBgGvQytDS0MWdS5DBdMBQyrdLEMV\\/kRDBRrhQyqLBkMVmMdDBKdeQyrJb0MVoRdDBKXyQypojUMVToFDBAKPQyqHBkMVDBhDA8GIQypbMEMUzu1DA32+QypLs0MVLqRDA16RQymogUMUtglDAv0nQypgF0MUgI5DAq\\/XQypD7UMUVBZDAnnxQyoS8kMUSaJDAllmQynWskMUNhlDAjnCQymFa0MT2GlDAgYzQylaU0MTiThDAb45QyoIh0MT+IdDAbxsQymlNEMT9gVDAfBRQyoUUUMUETZDAkC5Qypin0MUNn1DAm+XQyqvkEMUTUdDAi+PQyowrUMT+5NDAfQRQyo4pEMUKZtDAfjqQyqLcUMUY3ZDAjcLQynx8kMUPBtDAlXdQylh\\/EMTxz9DAhR\\/QypKGkMUhfNDAojFQytHukMVUQlDA1JSQypVDkMVQxlDA6fsQypAmUMVRzdDA\\/quQysJkEMVpptDBId6QytydUMWDXhDBNqJQyr8j0MWMiZDBR6eQyrxkkMWNFVDBR6nQyuppEMWeHlDBX4eQyvD00MWmu9DBZLBQyuWKUMWdydDBWn8QyvJy0MWVkNDBTOAQyuNSEMWM7xDBRgpQyt5lEMV8oVDBK42QyssJkMViD9DBBQ7QyqgdEMVEjxDA3O5QynxP0MUHjNDAlV7QylYKUMTI5tDAZkZQylb\\/EMTdv1DAdqwQyk6j0MTUJ9DAaZzQyltCEMTG4RDAVHlQyllrkMTAeJDAVzsQykeC0MTb8FDAfBuQylxX0MTqBJDAj7DQyi+W0MTi6xDAeLxQyhPz0MTSrFDAZ3PQyjBykMTt5lDAlCyQyfnCEMTKmJDAhk2QyjpJEMT9rFDAoG4QynjK0MU82pDA3H9Qyqjd0MVCFVDA8aYQyqfJ0MVFk5DA\\/pgQytspkMVweVDBImMQyvSJ0MWV51DBQtXQyvoPEMWTA5DBPa4QysdYEMVnOxDBB3iQypFLUMU+9JDBBDBQypCjEMUnsJDA2onQyltVkMUCNdDAugHQykgEEMTps9DApGNQyjcXUMTCD1DAfSJQyitcUMSkVJDAWWNQyfUp0MSUxRDAXDJ\",\"pixelsShape\":[597,1,3],\"bigEndian\":true,\"startTime\":28467070844000,\"endTime\":28487024091000,\"duration\":19953247000,\"fps\":30}"
//            "{\"base64Pixels\": \"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\", \"pixelsShape\": [166, 1, 3], \"bigEndian\": true, \"startTime\": 0.0, \"endTime\": 19.87558333333334, \"duration\": 19.87558333333334, \"fps\": 8.301643138356525}"
            "{\"base64Pixels\": \"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\", \"pixelsShape\": [200, 1, 3], \"bigEndian\": true, \"startTime\": 0.0, \"endTime\": 19.904400000000003, \"duration\": 19.904400000000003, \"fps\": 9.997789433492091}"
        )
        val json = signalJsons[0]
        val nativeAnalyzer = NativeAnalyzer()
        val roe = nativeAnalyzer.analyzeFromJson(json, com.vitals.lib.Port.copyBPModels(this))
        Log.d("VitalsDev", "testSignalJson: ${json.substring(17, 17+8)} $roe")
        roe.exception?.cause?.let {
            Log.d("VitalsDev", "testSignalJson: err case: $it")
        }
    }

    private fun compareSignalJsonBatch() {
        assets.open("sig_json.txt").use {
            val jsons = it.bufferedReader().readLines()
            for (json in jsons) {
                compareSignalJson(json)
            }
        }
    }

    private fun compareSignalJson(json: String) {
        val nativeAnalyzer = NativeAnalyzer()
        val roe = nativeAnalyzer.analyzeFromJson(json, com.vitals.lib.Port.copyBPModels(this))
        val head = json.substring(18, 18+8).replace("/", "_")
        Log.d("VitalsDev", "testSignalJson: $head $roe")
        val testDir = File("/sdcard/Android/data/com.vitals.example.app/cache/vitals/test")
        testDir.copyRecursively(File(testDir.path + "_" + head), overwrite = true)
        roe.exception?.cause?.let {
            Log.d("VitalsDev", "testSignalJson: $head err case: $it")
        }

        postPixels(json) {
            val res1 = roe.data
            val res2 = it
            if (res1 == null || res2 == null) {
                Log.e("VitalsDev", "testSignalJson: $head 无法对比，res1=$res1, res2=$res2")
                return@postPixels
            }
            val hrSame = res1.heartRate.roundToInt() == res2.heartRate.roundToInt()
            val confidenceSame = res1.confidence - res2.confidence < 0.01f
            if (hrSame) {
                Log.i("VitalsDev", "testSignalJson: $head 心率相同，sdk=${res1.heartRate}, server=${res2.heartRate}")
            } else {
                Log.e("VitalsDev", "testSignalJson: $head 心率不同，sdk=${res1.heartRate}, server=${res2.heartRate}")
            }
            if (confidenceSame) {
                Log.i("VitalsDev", "testSignalJson: $head 置信度相同, 差值=${res1.confidence - res2.confidence}，sdk=${res1.confidence}, server=${res2.confidence}")
            } else {
                Log.e(
                    "VitalsDev",
                    "testSignalJson: $head 置信度不同, 差值=${res1.confidence - res2.confidence}，sdk=${res1.confidence}, server=${res2.confidence}"
                )
            }
        }
    }

    private fun postPixels(jsonStr: String, callback: (MeasureResult?) -> Unit) {
        val client = OkHttpClient()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("jsonStr", jsonStr)
            .build()
        val req = Request.Builder()
            .url("http://192.168.1.15:51000/detect-with-pixels?use_new_bmi=1&age=59&gender=1&bmi=26.2&hr_high=130&hr_low=50&rr_low=9&rr_high=24")
            .post(body)
            .build()
        client.newCall(req).enqueue(object: okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.d("VitalsDev", "postPixels onFailure: err $e")
                callback(null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val data = response.body()?.string()
                Log.d("VitalsDev", "postPixels onResponse: resp $data")
                if (data == null) {
                    callback(null)
                    return
                }
                try {
                    val json = JSONObject(data)
                    val res = MeasureResult(
                        heartRate = json.getDouble("hr").toFloat(),
                        heartRateVariability = json.getDouble("hr").toFloat(),
                        respirationRate = json.getDouble("rr").toFloat(),
                        oxygenSaturation = json.getDouble("spo2").toFloat(),
                        systolicBloodPressure = json.getDouble("lbp").toFloat(),
                        diastolicBloodPressure = json.getDouble("hbp").toFloat(),
                        stress = json.getDouble("stress").toFloat(),
                        confidence = json.getDouble("peak_ratio").toFloat()
                    )
                    Log.d("VitalsDev", "postPixels onResponse: result $res")
                    callback(res)
                } catch (t: Throwable) {
                    Log.d("VitalsDev", "postPixels onResponse: err $t")
                    callback(null)
                }
            }
        })
    }
}