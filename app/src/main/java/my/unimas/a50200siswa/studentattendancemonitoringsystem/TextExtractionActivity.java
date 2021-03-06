package my.unimas.a50200siswa.studentattendancemonitoringsystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

import static my.unimas.a50200siswa.studentattendancemonitoringsystem.HomeActivity.isNetworkConnected;
import static org.opencv.core.Core.bitwise_not;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2GRAY;
import static org.opencv.imgproc.Imgproc.boundingRect;
import static org.opencv.imgproc.Imgproc.contourArea;

public class TextExtractionActivity extends AppCompatActivity {

    List<CroppedImageModel> listCroppedImages;

    RecyclerView RVCroppedImages;
    RecyclerViewAdapterCroppedImages CroppedImageAdapter;

    Button btnSignOut, btnUploadAttendance;
    TextView TVUserName, TVCourseCode, TVCourseName;
    CircleImageView userProfileImage;
    CircleImageView cvTEHome, cvTETakeAttendance;

    TextView EmptyViewCroppedImage, TVNoNetwork;

    String UserId,userName,CourseCode, CourseName,UserProfileImageUrl;

    String studentMatric, attendanceStatus;
    String croppedImageDirectory;
    String userID;
    ProgressBar ProgressUploadAttendance;

    /*------------------------- Firebase Database Element Declaration ----------------------------*/
    FirebaseAuth mAuth;
    FirebaseUser user;
    FirebaseAuth.AuthStateListener mAuthListener;
    DatabaseReference StudentsRef;
    /*--------------------------------------------------------------------------------------------*/


    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_extraction);
        OpenCVLoader.initDebug();
        /*------------------------- Receive data From Previous Intent ----------------------------*/
        Intent intent = getIntent();
        croppedImageDirectory = intent.getStringExtra("chunkedImagedDirectory");
        UserId = intent.getExtras().getString("UserId");
        userName = intent.getExtras().getString("UserName");
        CourseCode = intent.getExtras().getString("CourseCode");
        CourseName = intent.getExtras().getString("CourseName");
        UserProfileImageUrl = intent.getExtras().getString("UserProfileImageUrl");
        /*----------------------------------------------------------------------------------------*/

        btnSignOut = findViewById(R.id.btnsignout_home);
        TVUserName = findViewById(R.id.username);
        TVCourseCode = findViewById(R.id.tvtecoursecode);
        TVCourseName = findViewById(R.id.tvtecoursename);
        userProfileImage =findViewById(R.id.userprofileimg);
        btnUploadAttendance = findViewById(R.id.btnuploadattendance);
        TVNoNetwork= findViewById(R.id.tvnonetwork);

        cvTEHome =findViewById(R.id.cvtehome);
        cvTETakeAttendance =findViewById(R.id.cvtetakeattendance);

        ProgressUploadAttendance = findViewById(R.id.progressupload);

        EmptyViewCroppedImage =findViewById(R.id.empty_view_croppedimage);
        RVCroppedImages = findViewById(R.id.rvcroppedimage);
        RVCroppedImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL,false));

        /*----------------------------- Database Reference Elements ------------------------------*/
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        userID = user.getUid();
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        StudentsRef = rootRef.child("Users").child(userID).child("Course").child(CourseCode).child("Students");
        /*----------------------------------------------------------------------------------------*/

        TVUserName.setText(userName);
        TVCourseCode.setText(CourseCode);
        TVCourseName.setText(CourseName);
        GlideApp.with(TextExtractionActivity.this)
                .load(UserProfileImageUrl)
                .error(R.drawable.profilepic)
                .into(userProfileImage);

        cvTETakeAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TextExtractionActivity.this, TakeAttendancePictureActivity.class);
                intent.putExtra("UserId", UserId );
                intent.putExtra("UserName", userName );
                intent.putExtra("CourseCode", CourseCode);
                intent.putExtra("CourseName", CourseName);
                intent.putExtra("UserProfileImageUrl", UserProfileImageUrl);
                startActivity(intent);
                finish();
            }
        });

        cvTEHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder mBuilder = new AlertDialog.Builder(TextExtractionActivity.this);
                View view = (TextExtractionActivity.this).getLayoutInflater().inflate(R.layout.alert_goback, null);
                final Button btnYes = view.findViewById(R.id.btnteyes);
                final Button btnNo = view.findViewById(R.id.btnteno);
                mBuilder.setView(view);
                final AlertDialog dialog = mBuilder.create();
                dialog.show();

                btnYes.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(TextExtractionActivity.this, HomeActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });

                btnNo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

            }
        });


        final File fileDirectory = new File(croppedImageDirectory);

        listCroppedImages = new ArrayList<>();

        if (fileDirectory.isDirectory()) {

            EmptyViewCroppedImage.setVisibility(View.GONE);
            RVCroppedImages.setVisibility(View.VISIBLE);
            listCroppedImages.clear();
            String PhotoPath[] = new String[100];
            final String StudentMatric[] = new String[100];
            final String AttendanceRecord[] = new String[100];

            listCroppedImages.clear();

            for (int i = 1; i <= fileDirectory.listFiles().length; i++)
            {
                PhotoPath[i] = croppedImageDirectory + i + ".jpg";

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap croppedimageold = BitmapFactory.decodeFile(PhotoPath[i], options);
                Bitmap croppedimagenew = Bitmap.createScaledBitmap(croppedimageold, 460, 66, true);

                StudentMatric[i] = TextImageProcess(croppedimagenew);
                AttendanceRecord[i] = CircleDetection(croppedimagenew);
                listCroppedImages.add(new CroppedImageModel(String.valueOf(i), PhotoPath[i], StudentMatric[i], AttendanceRecord[i], CourseCode));
            }

            btnUploadAttendance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    int listSize = listCroppedImages.size() -1;

                    for (int x = 0; x < listCroppedImages.size(); x++) {
                        ProgressUploadAttendance.setVisibility(View.VISIBLE);
                        UploadData(listCroppedImages.get(x).getStudentMatric(),listCroppedImages.get(x).getAttendanceRecord(),x, listSize );
                    }
                }
            });

            CroppedImageAdapter = new RecyclerViewAdapterCroppedImages(this,listCroppedImages);
            RVCroppedImages.setAdapter(CroppedImageAdapter);


        } else {
            EmptyViewCroppedImage.setVisibility(View.VISIBLE);
            RVCroppedImages.setVisibility(View.GONE);
        }


        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();
            }
        });

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (firebaseAuth.getCurrentUser() == null) {
                    startActivity(new Intent(TextExtractionActivity.this, SignInActivity.class));
                }
            }
        };

        if (!isNetworkConnected(TextExtractionActivity.this)) {
            btnUploadAttendance.setVisibility(View.GONE);
            TVNoNetwork.setVisibility(View.VISIBLE);
        }else{
            btnUploadAttendance.setVisibility(View.VISIBLE);
            TVNoNetwork.setVisibility(View.GONE);
        }
    }


    public void UploadData(final String StudentMatric, final String AttendanceRecord, final int px, final int listSize ) {
        Query query = StudentsRef.orderByKey().equalTo(StudentMatric);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    DatabaseReference StudentMatricRef = StudentsRef.child(StudentMatric).child("Attendance").child(getCurrentDate());
                    StudentMatricRef.setValue(AttendanceRecord);
                    updateAttendance(StudentMatric, px, listSize);

                } else {
                    Toast.makeText(TextExtractionActivity.this, "Could not Find " + StudentMatric, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                throw databaseError.toException(); // don't ignore errors
            }
        });
    }

    public void updateAttendance(final String StudentMatric, final int px, int listSize){
        final int progress = px*100 / listSize;

        StudentsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int i = 0;
                int nop = 0;
                int noa = 0;
                String attendanceid[] = new String[35];
                String status[] = new String[35];

                for (DataSnapshot dataSnapshot1 : dataSnapshot.child(StudentMatric).child("Attendance").getChildren()) {
                    attendanceid[i] = dataSnapshot1.getKey();
                    status[i] = dataSnapshot.child(StudentMatric).child("Attendance").child(attendanceid[i]).getValue(String.class);

                    if (status[i].equals("Present")) {
                        nop++;
                    } else {
                        noa++;
                    }
                    i++;
                }

                if (noa + nop != 0) {
                    String Percentage = String.valueOf(AttendancePercentage(nop + noa, nop));
                    String TotalClass = String.valueOf(noa + nop);
                    String ClassAttended = String.valueOf(nop);
                    String ClassMissed = String.valueOf(noa);

                    DatabaseReference AttendanceRef = StudentsRef.child(StudentMatric).child("AttendanceRecord");
                    AttendanceRef.child("ClassAttended").setValue(ClassAttended);
                    AttendanceRef.child("ClassMissed").setValue(ClassMissed);
                    AttendanceRef.child("TotalClass").setValue(TotalClass);
                    AttendanceRef.child("Percentage").setValue(Percentage);

                    ProgressUploadAttendance.setProgress(progress);
                    if(progress == 100){
                        ProgressUploadAttendance.setVisibility(View.GONE);
                    }

                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w("Hello", "Failed to read value.", error.toException());
            }
        });

    }

    public String TextImageProcess(Bitmap bitmap) {
        TextRecognizer txtRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        if (!txtRecognizer.isOperational()) {
            //   tvProcessedText.setText("Try Again");
        } else {
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray items = txtRecognizer.detect(frame);
            StringBuilder strBuilder = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                TextBlock item = (TextBlock) items.valueAt(i);
                if(item.equals("S")){
                    strBuilder.replace(i,i, "5");
                }
                strBuilder.append(item.getValue());
            }
            studentMatric = strBuilder.toString().substring(0, strBuilder.toString().length());
        }
        return studentMatric;
    }
    public String CircleDetection(Bitmap bitmap) {

    //    String timeStamp = new SimpleDateFormat("yyyyMMdd_mmHH").format(new Date());
    //    String root = Environment.getExternalStorageDirectory().toString();
    //    File myDirCirecle = new File(root + "/sams_images/circle/");
    //    File myDirRectCrop = new File(root + "/sams_images/crop/");


        Mat src = new Mat();
        Utils.bitmapToMat(bitmap, src);
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.medianBlur(gray, gray, 5);

        Mat circles = new Mat();
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0,
                (double) gray.rows() / 8, // change this value to detect circles with different distances to each other .. previous value was 16
                100.0, 30.0, 13, 25); // change the last two parameters
        // (min_radius & max_radius) to detect larger circles
        Mat mask = new Mat(src.rows(), src.cols(), CvType.CV_8U, Scalar.all(0));
        int radius = 0;
        for (int x = 0; x < circles.cols(); x++) {
            double[] c = circles.get(0, x);
            Point center = new Point(Math.round(c[0]), Math.round(c[1]));
            // circle center
            //     Imgproc.circle(src, center, 1, new Scalar(0, 100, 100), 1, 8, 0);
            // circle outline
            radius = (int) Math.round(c[2]);

            Imgproc.circle(src, center, radius, new Scalar(255, 0, 255), 1, 8, 0);
            Imgproc.circle(mask, center, radius, new Scalar(255, 0, 255), 1, 8, 0);

            //String circledetected = myDirCirecle.toString() +m+"_"+x+ "_"+ String.valueOf(radius)+ "_"+".jpg";
            //Imgcodecs.imwrite(circledetected, src);
        }
        //String circledetected = myDir.toString() + "_" + String.valueOf(radius) + "_" + "a.jpg";
        //Imgcodecs.imwrite(circledetected, src);

        Mat masked = new Mat();
        src.copyTo(masked, mask);

        Mat thresh = new Mat();
        Imgproc.threshold(mask, thresh, 1, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(thresh, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        Mat cropped = null;
        Rect rect = null;
        for (int i = 0; i < contours.size(); i++) {
            rect = boundingRect(contours.get(i));
            if (rect.width / rect.height > 0.8 && rect.width / rect.height < 1.2 && rect.width > 30 && rect.height > 30) {
                cropped = src.submat(rect);
            }

          //  Mat newRec = src.submat(rect);
          //  String cropdetected = myDirRectCrop.toString() +m+"_" +i +"_"+ rect.height+"_"+rect.width + ".jpg";
          //  Imgcodecs.imwrite(cropdetected, newRec);
        }

        if (cropped == null) {
            attendanceStatus = "Failed";
            return attendanceStatus;
        }

         /*    String chunkedfilename = chunkedImagedDirectory + "_" + "present" + "h" + rectCrop.height + "w" + rectCrop.width + ".jpg";
            Imgcodecs.imwrite(chunkedfilename, imageROI);   */

           /*    String chunkedfilename = chunkedImagedDirectory + "absent" + "h" + rectCrop.height + "w" + rectCrop.width + ".jpg";
            Imgcodecs.imwrite(chunkedfilename, imageROI); */


        Mat localMat1 = cropped;
        Mat localMat2 = new Mat();
        Imgproc.GaussianBlur(localMat1, localMat2, new Size(5, 5), 7);
        Object localObject = new Mat();
        Imgproc.cvtColor(localMat2, (Mat) localObject, COLOR_RGB2GRAY);
        Mat cloneMat = ((Mat) localObject).clone();
        localMat2 = localMat1.clone();
        bitwise_not(cloneMat, cloneMat);
        Imgproc.threshold(cloneMat, localMat2, 127, 255, Imgproc.THRESH_OTSU);
        Mat thresh2 = localMat2.clone();

        List<MatOfPoint> contourscircles = new ArrayList<MatOfPoint>();

        Imgproc.findContours(localMat2, contourscircles, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Rect rectCrop = boundingRect(contourscircles.get(0));
        Mat imageROI = thresh2.submat(rectCrop);
        int total = countNonZero(imageROI);
        double pixel = total / contourArea(contourscircles.get(0)) * 100;
        if (pixel >= 70 && pixel <= 130) {
            attendanceStatus = "Present";
        } else {
            attendanceStatus = "Absent";
        }
        return attendanceStatus;

    }

    public String getCurrentDate() {
        String dateToday;
        Date today = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy_HH:mm", Locale.getDefault());
        dateToday = dateFormat.format(today);
        return dateToday;
    }

    public int AttendancePercentage(int totalClass, int classPresence){
        int Percentage;
        Percentage = classPresence * 100 / totalClass;
        return Percentage;
    }





}
