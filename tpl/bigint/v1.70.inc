#!/usr/bin/perl -w

# bigbench template file.
$| = 1;
use lib '##path##/Math-BigInt-1.70/lib';
use Math::BigInt;
use Math::BigFloat;
use Benchmark;
my $c = 'Math::BigInt';
my $cf = 'Math::BigFloat';

# output header and test for correct version

my $need = 'Math::BigInt v1.70 lib => Math::BigInt::Calc v0.40';
my $v = "Math::BigInt v$Math::BigInt::VERSION ";
if (Math::BigInt->can('config'))
  {
  $v .= "lib => ". Math::BigInt->config()->{'lib'};
  $v .= ' v' . Math::BigInt->config()->{'lib_version'};
  }
print "$v\n";

die "Cannot load '$need', got '$v'\n" unless $v eq $need;

# actual benchmarking code will be appended

