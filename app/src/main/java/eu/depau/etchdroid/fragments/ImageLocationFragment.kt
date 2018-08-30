package eu.depau.etchdroid.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.codekidlabs.storagechooser.StorageChooser
import eu.depau.etchdroid.R
import eu.depau.etchdroid.StateKeeper
import eu.depau.etchdroid.activities.WizardActivity
import eu.depau.etchdroid.adapters.PartitionTableRecyclerViewAdapter
import eu.depau.etchdroid.enums.FlashMethod
import eu.depau.etchdroid.enums.ImageLocation
import eu.depau.etchdroid.enums.WizardStep
import eu.depau.etchdroid.img_types.DMGImage
import eu.depau.etchdroid.kotlin_exts.getFileName
import eu.depau.etchdroid.kotlin_exts.snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_select_location.*
import java.io.File


/**
 * A placeholder fragment containing a simple view.
 */
class ImageLocationFragment : WizardFragment() {
    val READ_REQUEST_CODE = 42
    val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 29
    val TAG = "ImageLocationFragment"
    val PICKER_DIALOG_TAG = "eu.depau.etchdroid.filepicker.DIALOG_TAG"
    var issuesFound = false

    fun isStreamingAvailable(): Boolean {
        if (StateKeeper.imageLocation != ImageLocation.REMOTE)
            return false
        if (StateKeeper.flashMethod != FlashMethod.FLASH_DMG_API && StateKeeper.flashMethod != FlashMethod.FLASH_API)
            return false
        return true
    }

    override fun onRadioButtonClicked(view: View) {
        StateKeeper.imageLocation = ImageLocation.LOCAL
        activity?.fab?.show()
        pick_file_btn?.isEnabled = StateKeeper.imageLocation == ImageLocation.LOCAL
        loadImageChanges(activity as WizardActivity)
    }

    override fun onButtonClicked(view: View) {
        if (view.id == R.id.pick_file_btn) {
            when (StateKeeper.flashMethod) {
                FlashMethod.FLASH_API -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.setType("*/*");
                    activity?.startActivityForResult(intent, READ_REQUEST_CODE)
                }
                FlashMethod.FLASH_DMG_API -> {
                    if (checkAndRequestStorageReadPerm()) {
                        val sdcard = Environment.getExternalStorageDirectory().absolutePath


                        val chooser = StorageChooser.Builder()
                                .withActivity(activity)
                                .withFragmentManager(activity!!.fragmentManager)
                                .withMemoryBar(true)
                                .allowCustomPath(true)
                                .setType(StorageChooser.FILE_PICKER)
                                .customFilter(arrayListOf("dmg"))
                                .build()
                        chooser.show()
                        chooser.setOnSelectListener {
                            StateKeeper.imageFile = Uri.fromFile(File(it))
                            loadImageChanges(activity as WizardActivity)

                            activity?.fab?.show()
                        }
                    }
                }
                FlashMethod.FLASH_UNETBOOTIN -> {
                }
                FlashMethod.FLASH_WOEUSB -> {
                }
                null -> {
                }
            }
        }
    }

    fun checkAndRequestStorageReadPerm(): Boolean {
        if ((ContextCompat.checkSelfPermission(activity!!, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!,
                            Manifest.permission.READ_EXTERNAL_STORAGE)) {
                view!!.snackbar("Storage permission is required to read DMG images")
            } else {
                ActivityCompat.requestPermissions(activity!!,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
            }
        } else {
            // Permission granted
            return true
        }
        return false
    }

    override fun nextStep(view: View?) {
        if (issuesFound) {
            view?.snackbar(getString(R.string.issues_found_expl))
            return
        }

        if (StateKeeper.imageLocation == null) {
            view?.snackbar(getString(R.string.select_image_location))
            return
        }

        if (StateKeeper.imageFile == null) {
            view?.snackbar(getString(R.string.provide_image_file))
            return
        }

        (activity as WizardActivity).goToNewFragment(UsbDriveFragment())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                onButtonClicked(pick_file_btn)
                return
            }

            else -> {}
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fab?.show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        StateKeeper.currentFragment = this
        StateKeeper.wizardStep = WizardStep.SELECT_LOCATION

        return inflater.inflate(R.layout.fragment_select_location, container, false)
    }

    fun loadImageChanges(context: WizardActivity) {
        val button = pick_file_btn
        val uri = StateKeeper.imageFile ?: return

        val text = uri.getFileName(context)

        if (text != null)
            button.text = text
        else
            button.text = getString(R.string.pick_a_file)

        if (StateKeeper.flashMethod == FlashMethod.FLASH_DMG_API) {
            StateKeeper.imageRepr = DMGImage(uri, context)
            val imgRepr = StateKeeper.imageRepr as DMGImage

            if (imgRepr.tableType == null && (imgRepr.partitionTable == null || imgRepr.partitionTable?.size == 0)) {
                part_table_header.text = getString(R.string.image_is_not_dmg)
                issuesFound = true
                return
            } else {
                part_table_header.text = if (imgRepr.tableType != null) "Partition table:" else ""
                part_table_header_side.text = imgRepr.tableType?.getString(context) ?: ""
                issuesFound = false

                val viewAdapter = PartitionTableRecyclerViewAdapter(imgRepr.partitionTable!!)
                part_table_recycler.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(activity)
                    adapter = viewAdapter
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            var uri: Uri? = null
            if (data != null) {
                uri = data.getData()
                Log.d(TAG, "Uri: " + uri!!.toString())
                StateKeeper.imageFile = uri
                loadImageChanges(activity as WizardActivity)

                activity?.fab?.show()
            }
        }
    }
}
