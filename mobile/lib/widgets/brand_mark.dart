import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../theme/app_theme.dart';

/// Gold coin with "நா", the app name in Tamil and the Latin wordmark (login design).
class BrandMark extends StatelessWidget {
  const BrandMark({super.key});

  @override
  Widget build(BuildContext context) => Column(children: [
        Container(
          width: 88,
          height: 88,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: AppColors.gold,
            shape: BoxShape.circle,
            border: Border.all(color: const Color(0xFFC99A2E), width: 4),
          ),
          child: Container(
            width: 64,
            height: 64,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: const Color(0xFFA87C1F), width: 2),
            ),
            child: Text('நா',
                style: GoogleFonts.notoSansTamil(fontSize: 28, fontWeight: FontWeight.w700, color: AppColors.ink)),
          ),
        ),
        const SizedBox(height: 16),
        Text('நாணயம் தவணை',
            style: GoogleFonts.notoSansTamil(fontSize: 30, fontWeight: FontWeight.w700, color: AppColors.brand)),
        const SizedBox(height: 4),
        Text('NAANAYAM THAVANAI',
            style: GoogleFonts.spaceGrotesk(
                fontSize: 15, fontWeight: FontWeight.w600, letterSpacing: 0.6, color: AppColors.muted)),
      ]);
}
