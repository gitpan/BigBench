#!/usr/bin/perl 

# bigbench template file. This runs the benchmark suite under the original
# code.

$| = 1;
use lib '##path##/Math-BigInt-1.33/lib';
use Math::BigInt;
use Math::BigFloat;
use Benchmark;

# output header and test for correct version

my $need = 'Math::BigInt v1.33';
my $v = "Math::BigInt v$Math::BigInt::VERSION ";
if (Math::BigInt->can('_core_lib'))
  {
  $v .= "lib => " . Math::BigInt->_core_lib();
  $v .= " v" . eval '$'.Math::BigInt->_core_lib().'::VERSION';
  }
$v =~ s/[\s\n\t]*$//;
print "$v\n";

die "Can not load '$need', got '$v'\n" unless $v eq $need;

$cf = 'Math::BigFloat::Bench';
$c = 'Math::BigInt';

################################################################################
package Math::BigFloat::Bench;
use Exporter;
@ISA = qw/Exporter Math::BigFloat/;

sub fsqrt { die ('fsqrt() does not work reliable') }

package main;

my ($x,$y,$z);
# actual benchmarking code will be appended

