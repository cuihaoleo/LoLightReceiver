//-----------------------------------【头文件包含部分】---------------------------------------
//		描述：包含程序所依赖的头文件
//---------------------------------------------------------------------------------------------- 
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/opencv.hpp>
#include <stdio.h>
#include <math.h>
#include<iostream>
#include<fstream>


//-----------------------------------【命名空间声明部分】---------------------------------------
//		描述：包含程序所使用的命名空间
//----------------------------------------------------------------------------------------------- 
using namespace cv;
using namespace std;
#define pi 3.1415926
//-----------------------------------【main( )函数】--------------------------------------------
//		描述：控制台应用程序的入口函数，我们的程序从这里开始
//-----------------------------------------------------------------------------------------------
struct PT
{
	float x;
	float y;
};
struct LINE
{
	PT pStart;
	PT pEnd;
};
Point pl[5][1000];
int ct[5] = { 0,0,0,0,0 };
Point center(480, 270);
Point right_top;
Point right_but;
Point left_top;
Point left_but;

Point Pright_top;
Point Pright_but;
Point Pleft_top;
Point Pleft_but;

float kx, ky;
int vertical[1000];              //竖直
int horizonal[1000];             //水平

Point2f CrossPoint(const LINE *line1, const LINE *line2)
{
	Point2f pt;
	// line1's cpmponent
	double X1 = line1->pEnd.x - line1->pStart.x;//b1
	double Y1 = line1->pEnd.y - line1->pStart.y;//a1
												
	// line2's cpmponent
	double X2 = line2->pEnd.x - line2->pStart.x;//b2
	double Y2 = line2->pEnd.y - line2->pStart.y;//a2

	// distance of 1,2
	double X21 = line2->pStart.x - line1->pStart.x;
	double Y21 = line2->pStart.y - line1->pStart.y;
	// determinant
	double D = Y1*X2 - Y2*X1;// a1b2-a2b1
							 // 
	if (D == 0)
	{
		pt.x = 0;
		pt.y = 0;
		return pt;
	}
	// cross point
	pt.x = (X1*X2*Y21 + Y1*X2*line1->pStart.x - Y2*X1*line2->pStart.x) / D;
	// on screen y is down increased ! 
	pt.y = -(Y1*Y2*X21 + X1*Y2*line1->pStart.y - X2*Y1*line2->pStart.y) / D;
	// segments intersect.
	if (pt.x < 0 || pt.y < 0 || pt.x>1200 || pt.y>1200) 
	{
		pt.x = 0;
		pt.y = 0;
		return pt;
	}
	return pt;
}


int distance(Point s, Point e)
{
	return ((int)(sqrt(pow(abs((e.x - s.x)), 2) + pow(abs((e.y - s.y)), 2))));
}

void order_point(Point p[5][1000])
{
	ofstream fout("point.txt");
	int k;
	int i;
	int mind;
	for (k = 1; k <= 4; k++)
	{
		if (k == 1)
		{
			int d;
			mind = INT_MAX;
			for (i = 0; i < ct[k]; i++)
			{
				if ((d = distance(p[k][i], center)) < mind)
				{
					mind = d;
					right_but.x = p[k][i].x;
					right_but.y = p[k][i].y;
				}
			}
		}
		if (k == 2)
		{
			int d;
			mind = INT_MAX;
			for (i = 0; i < ct[k]; i++)
			{
				if ((d = distance(p[k][i], center)) < mind)
				{
					mind = d;
					left_but.x = p[k][i].x;
					left_but.y = p[k][i].y;
				}
			}
		}
		if (k == 3)
		{
			int d;
			mind = INT_MAX;
			for (i = 0; i < ct[k]; i++)
			{
				if ((d = distance(p[k][i], center)) < mind)
				{
					mind = d;
					left_top.x = p[k][i].x;
					left_top.y = p[k][i].y;
				}
			}
		}
		if (k == 4)
		{
			int d;
			mind = INT_MAX;
			for (i = 0; i < ct[k]; i++)
			{
				if ((d = distance(p[k][i], center)) < mind)
				{
					mind = d;
					right_top.x = p[k][i].x;
					right_top.y = p[k][i].y;
				}
			}
		}

	}


	cerr << endl;
	fout << left_top.x << " " << left_top.y << "\n";
	fout << right_top.x << " " << right_top.y << "\n";
	fout << right_but.x << " " << right_but.y << "\n";
	fout << left_but.x << " " << left_but.y << "\n";
	cerr << "right_but" << right_but.x << "    " << right_but.y << endl;
	cerr << endl;
	cerr << "right_top" << right_top.x << "    " << right_top.y << endl;
	cerr << endl;
	cerr << "left_but" << left_but.x << "    " << left_but.y << endl;
	cerr << endl;
	cerr << "left_top" << left_top.x << "    " << left_top.y << endl;
	Pright_top.x = (int)(right_top.x*kx); Pright_top.y = (int)(right_top.y*ky);
	Pright_but.x = (int)(right_but.x*kx); Pright_but.y = (int)(right_but.y*ky);
	Pleft_top.x = (int)(left_top.x*kx);  Pleft_top.y = (int)(left_top.y*ky);
	Pleft_but.x = (int)(left_but.x*kx); Pleft_but.y = (int)(left_but.y*ky);
	fout << Pleft_top.x << " " << Pleft_top.y << "\n";
	fout << Pright_top.x << " " << Pright_top.y << "\n";
	fout << Pright_but.x << " " << Pright_but.y << "\n";
	fout << Pleft_but.x << " " << Pleft_but.y << "\n";
	cerr << endl;
	cerr << "原图 right_but" << Pright_but.x << "    " << Pright_but.y << endl;
	cerr << endl;
	cerr << "原图 right_top" << Pright_top.x << "    " << Pright_top.y << endl;
	cerr << endl;
	cerr << "原图 left_but" << Pleft_but.x << "    " << Pleft_but.y << endl;
	cerr << endl;
	cerr << "原图 left_top" << Pleft_top.x << "    " << Pleft_top.y << endl;

	cout << Pleft_top.x << " " << Pleft_top.y << endl;
	cout << Pright_top.x << " " << Pright_top.y << endl;
	cout << Pright_but.x << " " << Pright_but.y << endl;
	cout << Pleft_but.x << " " << Pleft_but.y << endl;
}

int main(int argc, char *argv[])
{
	//【1】载入原始图和Mat变量定义   
	Mat midImage, dstImage;
	Mat GrayImage, BinaryImage;
	Mat srcImage = imread(argv[1]);
	int resize_height = 540;
	int resize_width = 960;
	cv::Mat dst;
	srcImage.copyTo(dst);
	kx = (dst.cols *1.0) / (resize_width*1.0);
	ky = (dst.rows*1.0) / (resize_height*1.0);
	cerr << "长度：" << dst.cols << " " << kx << "  " << "          宽度： " << dst.rows << "  " << ky << endl;
	cv::resize(srcImage, srcImage, cv::Size(resize_width, resize_height), (0, 0), (0, 0), cv::INTER_LINEAR);

	// 转为灰度图
	cvtColor(srcImage, GrayImage, CV_BGR2GRAY);

	// 创建二值图
	threshold(GrayImage, BinaryImage, 32, 255, CV_THRESH_BINARY);

	//【2】进行边缘检测和转化为灰度图
	Canny(BinaryImage, midImage, 100, 300, 3);//进行一此canny边缘检测
	Mat temp;
	midImage.copyTo(temp);

	Mat element2 = getStructuringElement(MORPH_RECT, Size(1, 5));
	dilate(midImage, midImage, element2, Point(-1, -1), 1);

	Mat element1 = getStructuringElement(MORPH_RECT, Size(5, 1));
	erode(midImage, midImage, element1, Point(-1, -1), 1);

	Mat element3 = getStructuringElement(MORPH_RECT, Size(5, 1));
	dilate(temp, temp, element3, Point(-1, -1), 1);

	Mat element4 = getStructuringElement(MORPH_RECT, Size(1, 5));
	erode(temp, temp, element4, Point(-1, -1), 1);

	addWeighted(midImage, 0.5, temp, 0.5, 0, midImage);


	cvtColor(midImage, dstImage, CV_GRAY2BGR);//转化边缘检测后的图为灰度图


	LINE pt1[10000];

	//【3】进行霍夫线变换
	vector<Vec4i> lines;//定义一个矢量结构lines用于存放得到的线段矢量集合
	HoughLinesP(midImage, lines, 1, CV_PI / 180, 80, 200, 10);

	//【4】依次在图中绘制出每条线段
	int e = 0;
	int v = 0;
	for (size_t i = 0; i < lines.size(); i++)
	{
		Vec4i l = lines[i];
		pt1[i].pStart.x = l[0];
		pt1[i].pStart.y = l[1];
		pt1[i].pEnd.x = l[2];
		pt1[i].pEnd.y = l[3];
		float tx, ty;
		ty = (l[3] - l[1]);
		tx = (l[2] - l[0]);
		float p;
		p = atan2(ty, tx);
		if (abs(p)< ((1 * 1.0) / (6 * 1.0) * pi))
		{
			horizonal[e++] = i;
			//line(dstImage, Point(l[0], l[1]), Point(l[2], l[3]), Scalar(255, 0, 0), 1, CV_AA);
		}
		if (abs(p) >((1 * 1.0) / (3 * 1.0) * pi) && abs(p) < ((2 * 1.0) / (3 * 1.0)* pi))
		{
			vertical[v++] = i;
			//line(dstImage, Point(l[0], l[1]), Point(l[2], l[3]), Scalar(255, 0, 0), 1, CV_AA);
		}

	}
	unsigned num = lines.size();
	int dis;

	Point t;
	for (int i = 0; i < e; i++)
	{
		for (int j = 0; j < v; j++)
		{
			Point2f cross = CrossPoint(&pt1[horizonal[i]], &pt1[vertical[j]]);
			if (cross.x != 0 && cross.y != 0)
			{
				t.x = (int)cross.x;
				t.y = (int)cross.y;
				dis = distance(t, center);
				if (t.x < center.x && t.y>center.y)
				{
					pl[2][ct[2]++] = t;
				}
				if (t.x > center.x && t.y>center.y)
				{
					pl[1][ct[1]++] = t;
				}
				if (t.x < center.x && t.y<center.y)
				{
					pl[3][ct[3]++] = t;
				}
				if (t.x > center.x && t.y<center.y)
				{
					pl[4][ct[4]++] = t;
				}

			}
		}
	}
	order_point(pl);
	
	vector<Point2f> corners(4);
	corners[0] = Pleft_top;
	corners[1] = Pright_top;
	corners[3] = Pleft_but;
	corners[2] = Pright_but;
	vector<Point2f> corners_trans(4);
	corners_trans[0] = Point2f(0, 0);
	corners_trans[1] = Point2f(960, 0);
	corners_trans[3] = Point2f(0, 540);
	corners_trans[2] = Point2f(960, 540);

	Mat transform = getPerspectiveTransform(corners, corners_trans);
	warpPerspective(dst, dst, transform, Size(961, 541));

	return 0;
}
