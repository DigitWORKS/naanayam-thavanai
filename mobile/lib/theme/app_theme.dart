import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Colours and type from the design canvas ("Naanayam Thavanai App Design").
class AppColors {
  AppColors._();

  static const brand = Color(0xFF0E6B4F);
  static const brandTint = Color(0xFFE6F2ED);
  static const background = Color(0xFFF4F6F5);
  static const surface = Color(0xFFFFFFFF);
  static const ink = Color(0xFF10231C);
  static const muted = Color(0xFF5B6B64);
  static const border = Color(0xFFC5D0CB);
  static const divider = Color(0xFFDCE3E0);
  static const warning = Color(0xFF8A4300);
  static const warningTint = Color(0xFFFFF4E5);
  static const gold = Color(0xFFE9B949);
  static const error = Color(0xFFB3261E);
}

class AppTheme {
  AppTheme._();

  /// Headings use Space Grotesk; body uses Inter with Noto Sans Tamil for Tamil text.
  static TextStyle heading(double size) =>
      GoogleFonts.spaceGrotesk(fontSize: size, fontWeight: FontWeight.w700, color: AppColors.ink);

  static ThemeData light() {
    final tamil = GoogleFonts.notoSansTamil().fontFamily!;
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.brand,
        primary: AppColors.brand,
        surface: AppColors.surface,
        error: AppColors.error,
      ),
      scaffoldBackgroundColor: AppColors.background,
    );
    final text = GoogleFonts.interTextTheme(base.textTheme)
        .apply(bodyColor: AppColors.ink, displayColor: AppColors.ink, fontFamilyFallback: [tamil]);

    final fieldBorder = OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: const BorderSide(color: AppColors.border, width: 1.5),
    );

    return base.copyWith(
      textTheme: text,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.surface,
        foregroundColor: AppColors.ink,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surface,
        border: fieldBorder,
        enabledBorder: fieldBorder,
        focusedBorder: fieldBorder.copyWith(borderSide: const BorderSide(color: AppColors.brand, width: 2)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.brand,
          minimumSize: const Size.fromHeight(56),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          textStyle: GoogleFonts.inter(fontSize: 17, fontWeight: FontWeight.w600),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.surface,
        surfaceTintColor: Colors.transparent,
        indicatorColor: AppColors.brandTint,
        height: 68,
        labelTextStyle: WidgetStateProperty.resolveWith((states) => GoogleFonts.inter(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: states.contains(WidgetState.selected) ? AppColors.brand : AppColors.muted,
            )),
        iconTheme: WidgetStateProperty.resolveWith((states) => IconThemeData(
              color: states.contains(WidgetState.selected) ? AppColors.brand : AppColors.muted,
            )),
      ),
      cardTheme: CardThemeData(
        color: AppColors.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    );
  }
}
