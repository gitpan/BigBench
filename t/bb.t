#!/usr/bin/perl -w

use Test;
use strict;
use File::Spec;

BEGIN
  {
  chdir 't' if -d 't';
  plan tests => 4;
  }

my ($options,$result);
my $zero = File::Spec->devnull();

while (<DATA>)
  {
  chomp();
  next if /^#/;			# skip comments
  $options = $_;
  print "Running ../bb $options\n";
  $result = `../bb $options 2>$zero`;
  ok (1,1);
  }


1;

__DATA__
--def=empty.def --templ=test
--def=no_op.def --templ=test
--def=test.def --templ=test
--help
