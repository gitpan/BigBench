#!/usr/bin/perl 

# bigbench template file. This runs the benchmark suite under the original
# code.

$| = 1;
use lib '##path##/Math-BigInt-0.01/lib';
use Math::BigInt;
use Math::BigFloat;
use Benchmark;

# output header and test for correct version

my $need = 'Math::BigInt v0.01';
my $v = "Math::BigInt v$Math::BigInt::VERSION";
print "$v\n";

die "Can not load '$need', got '$v'\n" unless $v eq $need;

$cf = 'Math::BigFloat::Bench';
$c = 'Math::BigInt::Bench';

###############################################################################
package Math::BigInt::Bench;
use Exporter;
@ISA = qw/Exporter Math::BigInt/;

sub copy { Math::BigInt::Bench->new(shift); }

################################################################################
package Math::BigFloat::Bench;
use Exporter;
@ISA = qw/Exporter Math::BigFloat/;

sub copy { Math::BigFloat::Bench->new(shift); }

package main;

my ($x,$y,$z);
# actual benchmarking code will be appended

