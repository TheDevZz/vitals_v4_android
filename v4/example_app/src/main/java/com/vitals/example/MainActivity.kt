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
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
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
            "{\"base64Pixels\":\"Qyp1x0MUslBDAfYrQyjqoUMTojFDATz5Qyp9\\/UMUZqBDAkYtQytQQ0MVEplDAuQUQyswzEMUgnZDAdThQyo3MkMTr0FDAPrmQyojnUMTtqFDAU4KQyq\\/4kMT0s9DAWHEQykDwUMR5L1C\\/pi3QyZ7T0MPchhC+pzFQybOW0MQUi5C+8UQQyf4OkMRJDhC\\/NJgQyabHEMP9+JC+uqpQybxeUMQI\\/tC+9tzQydw9EMQxNlC\\/PiNQydyp0MQzBFC\\/Nj0QybyJEMP3DNC+5DtQya6nEMPfmRC+lp7QyYlS0MPgsJC+ZSAQyYfDkMPG55C+MsFQyYMpkMOtA1C+AfRQyerfEMPxwFC+WmCQyYCNUMOrZpC+XavQyRr4kMNUspC9yJ7QyQFiEMNkVpC9qzOQyScz0MOCDxC95BXQyJZ70MMZG9C86lcQyGG4EMLiStC8pbxQySKL0MOA+9C9x9BQyVPC0MPYntC+UtAQySaoEMN95RC91inQyP44UMNJqhC9YrVQyUvw0MPKs9C+OCIQyPNPUMOG3dC948jQyYlf0MPnB1C+Z5kQyjTpkMR56dC\\/haJQyiBIkMSdPNDADgFQyeI\\/UMSC2tDAAvtQyrRREMUPoJDAaNKQy1shkMWVGBDAzDMQyu\\/y0MVbVVDAll0QyqReEMUFrVDAPiYQyov7kMTzfdDAEYVQyoq3EMTj2JDAKTxQyjQ0kMSRfFDAD0iQykqmEMSsB5DAI36QyjRkEMSxnRDAQ2YQyiHGkMSkDxDAMuDQyhC70MR4\\/hDAFhzQyibU0MSSahDAHFhQyY7HEMP02NC\\/LaMQyPm3kMNVqFC+DQ7QyYaUkMPPiNC+MDaQyVdFkMOcxhC9z+0QyTvVEMN54dC99hXQyTq8kMNqNNC94rUQyTt8kMN4Y1C9gdTQyVtYUMN5\\/lC9sJQQyTPBUMNWP5C9lkpQyTrWEMNiqpC9vLxQyZq6EMPFFJC+wi4Qyd6H0MP3fdC\\/HaBQykhwUMSVy1DAKQDQypWW0MT3IFDAi0zQyoFhkMTl1dDAozrQyqYXEMUDGVDAt27QypD+kMUhwhDAyTKQyo4bEMUq\\/lDA3w5Qypk\\/0MUoQ9DAyS+QyohZUMUkVZDAu4qQyqAYUMUes9DA1SsQyqRRUMUmT5DA6YmQyoGKUMU1AJDAwe4QyilC0MTs8NDAYBjQymW4EMTB45DAYEWQyk7y0MSfy1DAMsaQymBEkMTVgpDAM8XQyiZrEMSgWtC\\/+HXQyjbrEMSF2tC\\/+i3QylB1kMShkNDAFhNQyi1H0MSchhDAGBFQyftaEMSCVdC\\/\\/UiQyieX0MSb6NDAHwtQyjjQEMSuKtDAIpZQyiFJUMSWDNDAG7rQyekIUMR0mFDAE1FQygi8kMSD3pDAHimQykf6EMSuIFDAOB+Qygpm0MSEndDAH8fQybnGEMQ0DZC\\/3xSQyhaGEMR\\/lhDAJ3mQyk5pEMS7KtDATD5QyjPf0MScjtDAUTlQyjJZkMShqhDAUGlQyl9zkMTXE9DAYGiQymVd0MTPg9DANORQyiEfUMRpmRC\\/+c1QyiBFEMRkPFC\\/4uTQyifQUMRKu5C\\/ULuQyff+kMQupxC\\/FdJQyaJGkMPlbdC++BwQycvdUMP43NC\\/JJDQyY1ukMO+9tC+h6FQyS1s0MNhDlC98ItQyVhX0MOIgRC+WQQQyWbPkMOVkRC+dMaQyTQxkMN6ahC+MsUQyQ9B0MNYHNC99jNQyRvrUMOHvhC99VCQyRY7kMOQYdC982SQyQl50MN7MZC9vLAQyM84kMNEURC9WGtQyLF\\/0MMjuBC9HItQyNK60MMuDpC87ReQyOsT0MMlrlC88BnQyL5KkMMI6tC86Y\\/QyS03EMN6DZC9h\\/XQyUN20MOfehC93BuQyTXc0MOcjhC+O+6QyZWCEMQSKRC\\/RqnQyhxVkMR\\/FxC\\/t9YQyfuG0MRhVpC\\/nQZQyhzCUMScsxDAC+OQylBoEMTRzhDATevQyloqUMTAbNDALAZQykJR0MSzw9DAIHIQyls5kMTZWRDAUIyQymtEUMT8pFDAfZYQyr6cEMVCNNDAt9mQyvUg0MWFglDA+rhQy4SH0MX22lDBULGQy1Np0MXacRDBQnBQyvMjUMWcpdDBKngQyuTSkMWbxxDBHAyQyqu60MUdx5DAyLFQygAqEMRfRxDABclQykNwEMSL4tC\\/wofQycV90MQU3dC+7AdQyZtmkMPNUhC+ppAQyZRAEMOx7FC+Q38QyfcqkMQeVhC+pqeQyhxiUMQ+HNC+67lQyjcMEMRvNZC\\/SWAQyj1x0MR3lZC\\/YmjQyfPXUMRIBRC\\/WcpQydkQ0MQmZtC\\/Pw8QycpxEMQZlxC\\/Q3sQydzdUMQjxtC\\/PVTQyeat0MRGp9C\\/0PSQydROUMQlE9C\\/pHYQyg98kMRo0FDAJK6QyjW6UMSs9ZDAWZNQyjnY0MSnuNDAZUXQynVz0MTStlDAoLiQyjWI0MSnMVDAXWWQydBlEMRfedDAC4rQycy80MRNDhC\\/91CQycx5kMRGRpDAACgQybw5kMQzypC\\/SegQyZ45UMQIqpC+6xhQyb47EMQXMRC\\/P1cQyY2QkMPmgFC\\/A\\/NQyYZv0MPPbdC+6jxQyYlVEMPGcVC+5EIQyYSLEMPLllC+rLvQyW0wUMOxeBC+bpYQyc750MQCahC+224QydPSUMQsEFC\\/LG+QyakSUMQdLBC\\/I\\/yQybYE0MQdCJC\\/VMrQyaz9UMQef5C\\/QTuQyaoDEMQarhC\\/TjwQydpOkMQrf1C\\/gAOQyZ\\/kkMQBcBC\\/PGqQyar7UMQNIdC\\/Mn2QydGKEMQWrdC\\/FHgQya93kMP2KhC+2tnQyajW0MPyyJC+yKmQyaotUMPepJC+drPQyYmqEMOuINC+MpAQyVGqkMOdCVC96y9QyVZ4kMOfNJC9zDPQyTP4UMN+5RC9i4XQyQPaEMNYUVC9JDuQyTG50MNlpBC9Vq3QyPMG0MMsjpC8xHoQyOWbEMMXTtC9Bc2QyQOKkMNJ5dC9hNIQyT7cUMOc+FC+UBeQyZ1oUMPu4ZC+848Qyd++0MQo3RC\\/sJlQyf2E0MRRyZDADPhQyhUrkMRkDpC\\/5bGQyjNs0MSHzVDAF7pQyffEkMSAyVDABCgQyf2hEMSGzxDAEEKQykWVUMTZPpDAUWmQynAh0MT1VpDAcJgQyoz50MUkTVDAe+PQyptq0MU1JlDAls2Qyqix0MVDHdDApGFQyvKZkMWEZRDA23QQyrAm0MU47tDAm0iQyl32EMTlDdDAPzPQytuBEMUva1DAmcEQytAiUMVGJdDApRzQyrIo0MVIR9DAthsQyqIcUMUnEdDAkmOQysTE0MUzWRDAjvxQytz\\/EMU\\/HxDApyxQyq8UkMUf6FDAjE7QyoHKUMTumhDAUX6QymdqUMTx3hDAYjgQykp4UMTK0VDALpbQyllhEMS5LVDADqjQymeckMTZQlDAKavQyqIWUMUTZ5DAdciQysnaUMU+vpDAr8HQyt1nEMVIRtDAr7ZQyt54EMVHb5DAp9XQyq71EMUibxDAhrRQyjteEMSyZFDALlzQyguMUMR3UhC\\/512QydeakMQr5hC\\/NizQycDHUMQU+dC\\/BG8QyY0W0MPRslC+l9EQyZJi0MPV6dC+a3KQyaut0MPmFFC+m8qQybxo0MP8FdC+u7yQyapTUMPvuRC+t0hQydV2kMQcxlC+\\/SCQyePlkMQuDFC\\/FoUQycsKkMQMIVC++0mQyX9NEMPUchC+oEGQyZ1ekMPH8NC+v2cQyaumUMPYLBC+xRzQyaaOEMPtdpC+vbMQyZjLEMPsJBC+t9FQycvIUMQl1JC\\/YGeQygSwkMRbBBC\\/4y3QyfzzkMRNfFC\\/74WQyhr0UMR2AdDAE7vQyh3MUMRo+xDABAbQygnD0MRNDlC\\/5kTQyhCuEMRawZC\\/WCmQycwPkMQiL1C+92OQydk00MQVApC+6fNQyc53kMQNdZC+z4bQycFxEMQD5hC+sEKQydpLkMQdllC+7C8QydfdkMQg0lC+zDlQybbG0MQDjBC+oi+QyaIuEMP\\/C5C\\/EBpQyXRaEMPhZJC+5NtQyXTrkMPMQhC+9CxQyXN20MPYMJC+5ESQyZ0dEMP1+5C+yZEQySqOEMOjudC+WTxQyZarUMPqmNC+5dfQyeUX0MQf5NC\\/P9yQyaUFkMQRm5C+\\/TxQyaN4UMQV0VC+7jQQyacK0MP5LtC+8kxQyZOCEMPdJ9C+W5lQyVwFUMO25ZC+Nk9QyT9bkMOdwdC+GZwQyUX3kMOybVC+RAPQyVD5EMO\\/qpC+RjjQyYO9kMPPUNC+cSIQyXtNUMPFrdC+e8JQyXtVkMPjftC+lFKQyZhe0MQLoRC+1BlQybf2UMP\\/0VC+zCnQyfNIEMQpZVC\\/LLsQyff3UMRHYxC\\/hyPQyegnEMRL8FC\\/c8VQybV\\/kMQp2ZC\\/nwDQyadfEMQbo5C\\/eS+QyZAY0MP\\/\\/VC\\/Za3QyYlYkMP8d1C\\/Y+lQycszkMQQEFC\\/gpDQybPXUMQH8FC\\/dyZQygA5UMQ1hpC\\/YVsQyhxtEMRM2NC\\/f\\/5Qye8hkMQ3RtC\\/W0eQyh6jkMRd9xC\\/mIeQyipf0MRnolC\\/ydRQyg4eEMRWvZC\\/snJQyeNAEMRv2pC\\/4r1QyepNkMR3rtDABNQQycQwUMRpXxC\\/9vBQyb1W0MRwwJDAAgdQyiVBkMSW41DAJuyQymkiEMTNyRDARB0QyiE70MSQB5DAP8PQydo2UMRUTpDACkyQyiatUMSKXtC\\/8aqQyidDUMRp3VC\\/4zJQyenREMRNO1C\\/u4YQyct20MRK+lC\\/5I3QygZl0MR3n5DACjMQylGD0MSv65DAPIQQyhQF0MS0p1DAJfTQyeM6EMSXhBDAE5ZQyiSTEMTVINDAOPcQyi3GEMTXotDAQ\\/1Qyjg4kMTaCpDARC5QyiDIUMTTlxDAPIFQyiYJ0MTZtZDAP5NQykaBUMTw8JDAX0rQyjbWEMTBgRDAODpQyiGV0MSiM5DAIprQynHWEMTTd5DAMf+Qyl2HEMTCoJDAKk4QylOUkMTF9BDAJ6RQym4e0MTObJDAJSuQyoGKUMTWsVDAPFVQyke3UMSviBDADf7QyiJyUMSQltC\\/17hQyjXtkMSS+5C\\/yO2QykFhEMSvRdC\\/6kRQygoeEMSFyFC\\/rtkQyjuX0MSqWxDAFX5QymevUMTWuVDAOZyQyj7UkMSnytDAEJKQyi5nkMSg6pDAAxLQylNo0MSzvJDAD3VQykr+UMSvqNDAA+3Qyj+zEMSkRdDAGHXQyfZM0MRgMtC\\/pR8Qyg9CEMRyQRC\\/zBtQyiVcEMR83BDAAqrQygb5kMSPIhDAEo8QyiQtUMSrrRDANMsQyi+mEMTIedDAV7XQykIV0MTftpDAaQLQylymkMTtmVDAe+wQyk4MUMTlBZDAebkQymIHUMT0WhDAg8qQymQNUMTu3JDAfd\\/QyoHJUMT3dlDAft7QyoN7UMTtY9DAcz2QypiLkMTu+1DAaJRQypocUMT+O1DAdobQypKVUMUR\\/VDAndiQyoxrkMUQENDAoQuQypDtUMUfINDAnPPQypuYEMUbtNDAqODQyqEk0MUZmpDAoUnQym7DUMT2HNDActeQyljXkMTg8hDAbWhQynbaUMTb8lDAdYUQyl67kMTgvBDAUuTQynCBkMUCG5DAb4nQynMgUMT\\/bVDAkrcQynXHkMUGmBDAjqwQyoEO0MUaaZDAmDHQymxo0MUSPZDAlG6Qynd\\/kMUAdxDAnX1QymYvUMT1gBDAf1IQyoOaEMUFcNDAiuJQyp4kUMUZ2ZDAmbLQyqD4kMUEVxDAfl2QynoNUMTbBZDAWK4Qyk0IkMTAtZDATMWQyiFQkMSlUZDAPobQyjIt0MSTwxDAEx8QyjcoEMSU\\/hDACDmQyktB0MSsqhDAL5SQymyRUMS4FhDAMdQQykqrEMS12FDAKCHQyjMt0MSdf1DAFxNQykhY0MS3FVDAJvuQyleeUMTBv5DAKwPQynA6EMTPtJDAQzKQynG3kMTbgFDAP0\\/QyogykMT6QhDAToOQyi8H0MSx+1DALwdQyn0X0MTzHRDAdE8Qyrd4EMU4bdDAqIDQynClUMTyGJDAbvjQynOX0MUCTJDAg9QQynq30MUNdVDAnGjQynXdUMUPH9DApP\\/QynvBkMUMINDAk+oQylOtEMTzRBDAbnZQyjaR0MTQCRDAXmqQyiq3UMS4oBDASdCQyj4AUMS9s5DAXtTQyjYbEMTDLBDAU1cQykQ2kMTJN9DARzYQyj9XEMS\\/AFDARA5QyiDq0MSta9DAOReQyhqzUMSnONDAMCZQyfbHkMSg7NDAH8qQyf4L0MSq+hDAJUjQyi3r0MTKzBDAZKzQygHP0MS1vBDAYwAQym2\\/EMUHGRDApmbQyqOPUMVEuRDA4CMQyoBOkMU4E9DA43iQyqAl0MVMsZDA+pnQypznkMU2yNDA27qQymmlkMUlFdDAwu7Qypa\\/0MVL51DA8jLQyqo4UMVqH1DBBVAQyrWNUMVvr9DBDcWQyrwvUMV3YJDBGMTQytCMUMWAXhDBGnBQyqsc0MVXB9DA9DxQyneDEMUYHZDApe9Qyhqh0MStvZDANKYQyi5q0MSkyBDALATQyf+DEMSNWhDAGYoQyfZxkMRtVBC\\/6GcQyc5CkMREFZC\\/jUPQya7CEMQYslC\\/NnUQyYUXUMPojFC+8lsQyXcrUMPQjZC+knbQyWzCUMO9lZC+hf3QyU8f0MPBxFC+iHcQyT\\/MEMOtpFC+cQLQySzdkMOZ3NC+eNsQyS3aUMOXIhC+ii0QyUNRkMOl51C+covQyYEyEMPXhZC+r4HQyT+60MOtLBC+q1VQyRu4kMOOkZC+iCuQyUN60MOm89C+eOkQyXCbUMPN8pC+vqFQyWIg0MPC9BC+nElQyTibkMOmuNC+eohQyYEi0MPZk9C+2kiQycdo0MQoe1C\\/YYEQycTqkMRNIJC\\/uZ5QydRhEMRm7lDAExgQyhxnkMSooVDAQ6hQyj6qUMTMEJDAXxhQyi9F0MTdWJDAYjeQyi2uUMTUlhDAdlkQylPyEMTvalDAgijQykz40MThaFDAgGYQyjLfEMTgIJDAh1aQylxBUMT6WxDAlsUQyo1b0MUl49DAtIPQyk0hUMUA01DAq+hQypOYUMVKAdDA5bLQyuXJ0MWWb1DBL2ZQyqdnUMVt8FDBB7kQyokrEMVgehDA\\/RiQysvDUMV2NVDBHDuQytM8UMV\\/8pDBJNaQyrbm0MV4itDBHVhQyrxsUMWHHRDBM3GQyusGEMWZylDBQhCQyufHkMWe9hDBQHaQywcVUMWPTFDBHHgQyxri0MWlldDBJRvQysZ2UMVlylDBAw9QyngikMUgDVDAuL7Qyo\\/I0MUwJpDApgoQyo+1EMUZZZDAnitQyoGmEMT94pDAlKmQym9MEMT9L9DAi7UQynehkMUYlFDApMaQyk4KEMT7KtDAjaPQykVKEMTfY9DAS7XQylYuUMTefZDASE0QymQS0MT\\/NtDAeOaQyiW1UMTLVdDAU5CQynnPEMUE29DAaIiQyp+EkMUzVVDAl8RQyo36kMUOoFDAjXcQyp110MUuh1DAszZQyptBEMU8YZDAthiQypxGEMVD+9DAwjlQysNC0MVh2FDA8wAQyqNvkMVIKlDA5kKQyq8o0MVa0FDA6EMQyrXiEMVaK9DA5TcQyqZoUMVLNBDA5DyQypst0MVQ15DA7szQypwkUMVc2hDA9GHQypitUMVhA9DA+mpQyqDe0MVTlVDA8LJQyrWP0MVdftDA+GOQyrnWUMVig5DA8r9QyqOS0MVZeBDA9EgQyohpEMU70pDA4LrQyoqNkMU4clDA4t1Qyml9EMUIzlDAuLgQyiZ1kMTNRNDAdYRQymXUUMThCtDAdFSQyldVkMTUXJDAbU0QykNRkMTE31DAZjCQyjszEMSxAtDARVKQyiZ+kMS+6VDARhJQykCgEMTPAlDAY\\/rQykMNkMS0l1DAR2UQyiE9UMSVYVDAEbuQyioVUMSlZpDAN3oQyirNkMSssBDAQohQyjRp0MS5CZDAVIeQykCsEMS4b5DARg2Qyg90kMSLpRDAL8xQyhalkMSG0hDAJOTQyfXmkMRooFC\\/vV3QyfBAUMRWXRC\\/lreQygwKkMR601C\\/8h5Qyek6UMRPiBC\\/tmmQyeBWkMQ+GBC\\/gA4QycW6kMQyhNC\\/YC2QybRy0MQtidC\\/j9YQyarSUMQfAJC\\/iL3Qyclo0MRMVtC\\/t7yQye++UMR07tDAD8tQygNKUMSIoRDAJr7QyfW1UMRzxxDAGjJQyiXCEMS4KdDAbjQQyjhdkMTi8FDAm07QykyQkMUDTBDAxvMQynEi0MUxBtDBArNQyl7X0MVCqNDBF9aQymjc0MVNVNDBJl7QyrNkkMWG65DBZpXQytOikMWkhdDBgosQytleUMWyWNDBjcVQytRv0MWlbRDBfH\\/QytK8kMWiylDBgGvQytDS0MWdS5DBdMBQyrdLEMV\\/kRDBRrhQyqLBkMVmMdDBKdeQyrJb0MVoRdDBKXyQypojUMVToFDBAKPQyqHBkMVDBhDA8GIQypbMEMUzu1DA32+QypLs0MVLqRDA16RQymogUMUtglDAv0nQypgF0MUgI5DAq\\/XQypD7UMUVBZDAnnxQyoS8kMUSaJDAllmQynWskMUNhlDAjnCQymFa0MT2GlDAgYzQylaU0MTiThDAb45QyoIh0MT+IdDAbxsQymlNEMT9gVDAfBRQyoUUUMUETZDAkC5Qypin0MUNn1DAm+XQyqvkEMUTUdDAi+PQyowrUMT+5NDAfQRQyo4pEMUKZtDAfjqQyqLcUMUY3ZDAjcLQynx8kMUPBtDAlXdQylh\\/EMTxz9DAhR\\/QypKGkMUhfNDAojFQytHukMVUQlDA1JSQypVDkMVQxlDA6fsQypAmUMVRzdDA\\/quQysJkEMVpptDBId6QytydUMWDXhDBNqJQyr8j0MWMiZDBR6eQyrxkkMWNFVDBR6nQyuppEMWeHlDBX4eQyvD00MWmu9DBZLBQyuWKUMWdydDBWn8QyvJy0MWVkNDBTOAQyuNSEMWM7xDBRgpQyt5lEMV8oVDBK42QyssJkMViD9DBBQ7QyqgdEMVEjxDA3O5QynxP0MUHjNDAlV7QylYKUMTI5tDAZkZQylb\\/EMTdv1DAdqwQyk6j0MTUJ9DAaZzQyltCEMTG4RDAVHlQyllrkMTAeJDAVzsQykeC0MTb8FDAfBuQylxX0MTqBJDAj7DQyi+W0MTi6xDAeLxQyhPz0MTSrFDAZ3PQyjBykMTt5lDAlCyQyfnCEMTKmJDAhk2QyjpJEMT9rFDAoG4QynjK0MU82pDA3H9Qyqjd0MVCFVDA8aYQyqfJ0MVFk5DA\\/pgQytspkMVweVDBImMQyvSJ0MWV51DBQtXQyvoPEMWTA5DBPa4QysdYEMVnOxDBB3iQypFLUMU+9JDBBDBQypCjEMUnsJDA2onQyltVkMUCNdDAugHQykgEEMTps9DApGNQyjcXUMTCD1DAfSJQyitcUMSkVJDAWWNQyfUp0MSUxRDAXDJ\",\"pixelsShape\":[597,1,3],\"bigEndian\":true,\"startTime\":28467070844000,\"endTime\":28487024091000,\"duration\":19953247000,\"fps\":30}"
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
        Log.d("VitalsDev", "testSignalJson: ${json.substring(17, 17+8)} $roe")
        roe.exception?.cause?.let {
            Log.d("VitalsDev", "testSignalJson: err case: $it")
        }

        postPixels(json) {
            val res1 = roe.data
            val res2 = it
            if (res1 == null || res2 == null) {
                Log.e("VitalsDev", "testSignalJson: 无法对比，res1=$res1, res2=$res2")
                return@postPixels
            }
            val hrSame = res1.heartRate.roundToInt() == res2.heartRate.roundToInt()
            val confidenceSame = res1.confidence - res2.confidence < 0.01f
            if (hrSame) {
                Log.i("VitalsDev", "testSignalJson: 心率相同，sdk=${res1.heartRate}, server=${res2.heartRate}")
            } else {
                Log.e("VitalsDev", "testSignalJson: 心率不同，sdk=${res1.heartRate}, server=${res2.heartRate}")
            }
            if (confidenceSame) {
                Log.i("VitalsDev", "testSignalJson: 置信度相同, 差值=${res1.confidence - res2.confidence}，sdk=${res1.confidence}, server=${res2.confidence}")
            } else {
                Log.e(
                    "VitalsDev",
                    "testSignalJson: 置信度不同, 差值=${res1.confidence - res2.confidence}，sdk=${res1.confidence}, server=${res2.confidence}"
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
            .url("http://192.168.1.10:51000/detect-with-pixels?use_new_bmi=1&age=59&gender=1&bmi=26.2&hr_high=130&hr_low=50&rr_low=9&rr_high=24")
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